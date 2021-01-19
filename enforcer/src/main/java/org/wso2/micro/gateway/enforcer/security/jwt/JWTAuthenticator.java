/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.micro.gateway.enforcer.security.jwt;

import com.google.common.cache.LoadingCache;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.util.DateUtils;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wso2.micro.gateway.enforcer.api.RequestContext;
import org.wso2.micro.gateway.enforcer.common.CacheProvider;
import org.wso2.micro.gateway.enforcer.common.ReferenceHolder;
import org.wso2.micro.gateway.enforcer.config.ConfigHolder;
import org.wso2.micro.gateway.enforcer.config.EnforcerConfig;
import org.wso2.micro.gateway.enforcer.config.dto.TokenIssuerDto;
import org.wso2.micro.gateway.enforcer.constants.APIConstants;
import org.wso2.micro.gateway.enforcer.constants.APISecurityConstants;
import org.wso2.micro.gateway.enforcer.dto.APIKeyValidationInfoDTO;
import org.wso2.micro.gateway.enforcer.exception.APISecurityException;
import org.wso2.micro.gateway.enforcer.exception.MGWException;
import org.wso2.micro.gateway.enforcer.security.AuthenticationContext;
import org.wso2.micro.gateway.enforcer.security.Authenticator;
import org.wso2.micro.gateway.enforcer.security.TokenValidationContext;
import org.wso2.micro.gateway.enforcer.security.jwt.validator.JWTValidator;
import org.wso2.micro.gateway.enforcer.security.jwt.validator.RevokedJWTDataHolder;
import org.wso2.micro.gateway.enforcer.util.FilterUtils;

import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Implements the authenticator interface to authenticate request using a JWT token.
 */
public class JWTAuthenticator implements Authenticator {

    private static final Logger log = LogManager.getLogger(JWTAuthenticator.class);
    private JWTValidator jwtValidator = new JWTValidator();
    private boolean isGatewayTokenCacheEnabled;

    @Override
    public boolean canAuthenticate(RequestContext requestContext) {
        String jwt = requestContext.getHeaders().get("authorization");
        if (jwt != null && jwt.split("\\.").length == 3) {
            return true;
        }
        return false;
    }

    @Override
    public AuthenticationContext authenticate(RequestContext requestContext) throws APISecurityException {
        String jwtToken = requestContext.getHeaders().get("authorization");
        String splitToken[] = jwtToken.split("\\s");
        // Extract the token when it is sent as bearer token. i.e Authorization: Bearer <token>
        if (splitToken.length > 1) {
            jwtToken = splitToken[1];
        }
        String context = requestContext.getMathedAPI().getAPIConfig().getBasePath();
        String name = requestContext.getMathedAPI().getAPIConfig().getName();
        String version = requestContext.getMathedAPI().getAPIConfig().getVersion();
        context = context + "/" + version;
        String matchingResource = requestContext.getMatchedResourcePath().getPath();
        String httpMethod = requestContext.getMatchedResourcePath().getMethod().toString();
        SignedJWTInfo signedJWTInfo;
        try {
            signedJWTInfo = getSignedJwt(jwtToken);
        } catch (ParseException | IllegalArgumentException e) {
            throw new SecurityException("Not a JWT token. Failed to decode the token header.", e);
        }
        String jti;
        JWTClaimsSet claims = signedJWTInfo.getJwtClaimsSet();
        jti = claims.getJWTID();

        String jwtHeader = signedJWTInfo.getSignedJWT().getHeader().toString();
        if (StringUtils.isNotEmpty(jti)) {
            if (RevokedJWTDataHolder.isJWTTokenSignatureExistsInRevokedMap(jti)) {
                if (log.isDebugEnabled()) {
                    log.debug("Token retrieved from the revoked jwt token map. Token: "
                            + FilterUtils.getMaskedToken(jwtHeader));
                }
                log.error("Invalid JWT token. " + FilterUtils.getMaskedToken(jwtHeader));
                throw new APISecurityException(APISecurityConstants.API_AUTH_INVALID_CREDENTIALS, "Invalid JWT token");
            }

        }

        JWTValidationInfo validationInfo = getJwtValidationInfo(signedJWTInfo, jti);
        if (validationInfo != null) {
            if (validationInfo.isValid()) {

                // Validate subscriptions
                APIKeyValidationInfoDTO apiKeyValidationInfoDTO = null;
                EnforcerConfig configuration = ConfigHolder.getInstance().getConfig();
                TokenIssuerDto issuerDto = configuration.getIssuersMap().get(validationInfo.getIssuer());
                  //TODO: enable subscription validation
                if (issuerDto.isValidateSubscriptions()) {

                    JSONObject api = validateSubscriptionFromClaim(name, version, claims, splitToken, true);
                    if (api == null) {
                        if (log.isDebugEnabled()) {
                            log.debug("Begin subscription validation via Key Manager: "
                                    + validationInfo.getKeyManager());
                        }
                        apiKeyValidationInfoDTO = validateSubscriptionUsingKeyManager(requestContext, validationInfo);
                        if (log.isDebugEnabled()) {
                            log.debug("Subscription validation via Key Manager. Status: " + apiKeyValidationInfoDTO
                                    .isAuthorized());
                        }
                        if (!apiKeyValidationInfoDTO.isAuthorized()) {
                            throw new APISecurityException(apiKeyValidationInfoDTO.getValidationStatus(),
                                    "User is NOT authorized to access the Resource. " +
                                            "API Subscription validation failed.");
                        }
                    }
                }
                // Validate scopes
                validateScopes(context, version, matchingResource, httpMethod, validationInfo, signedJWTInfo);

                log.debug("JWT authentication successful.");
                String endUserToken = null;
                //                if (jwtGenerationEnabled) {
                //                    JWTInfoDto jwtInfoDto = GatewayUtils
                //                            .generateJWTInfoDto(jwtValidationInfo, apiKeyValidationInfoDTO, synCtx);
                //                    endUserToken = generateAndRetrieveJWTToken(jti, jwtInfoDto);
                //                }
                AuthenticationContext authenticationContext = FilterUtils.generateAuthenticationContext(jti,
                        validationInfo, apiKeyValidationInfoDTO, endUserToken, true);
                //TODO: (VirajSalaka) Place the keytype population logic properly for self contained token
                if (claims.getClaim("keytype") != null) {
                    authenticationContext.setKeyType(claims.getClaim("keytype").toString());
                }
                return authenticationContext;
            } else {
                requestContext.getProperties().put("code", "401");
                requestContext.getProperties().put("error_code", "900901");
                requestContext.getProperties().put("error_description", "Invalid credentials");
                throw new APISecurityException(validationInfo.getValidationCode(),
                        APISecurityConstants.getAuthenticationFailureMessage(validationInfo.getValidationCode()));
            }
        } else {
            throw new APISecurityException(APISecurityConstants.API_AUTH_GENERAL_ERROR,
                    APISecurityConstants.API_AUTH_GENERAL_ERROR_MESSAGE);
        }

    }

    /**
     * Validate scopes bound to the resource of the API being invoked against the scopes specified
     * in the JWT token payload.
     *
     * @param apiContext        API Context
     * @param apiVersion        API Version
     * @param matchingResource  Accessed API resource
     * @param httpMethod        API resource's HTTP method
     * @param jwtValidationInfo Validated JWT Information
     * @param jwtToken          JWT Token
     * @throws APISecurityException in case of scope validation failure
     */
    private void validateScopes(String apiContext, String apiVersion, String matchingResource, String httpMethod,
            JWTValidationInfo jwtValidationInfo, SignedJWTInfo jwtToken) throws APISecurityException {
        try {
            String tenantDomain = "carbon.super"; //TODO : Derive proper tenant domain.

            // Generate TokenValidationContext
            TokenValidationContext tokenValidationContext = new TokenValidationContext();

            APIKeyValidationInfoDTO apiKeyValidationInfoDTO = new APIKeyValidationInfoDTO();
            Set<String> scopeSet = new HashSet<>();
            scopeSet.addAll(jwtValidationInfo.getScopes());
            apiKeyValidationInfoDTO.setScopes(scopeSet);
            tokenValidationContext.setValidationInfoDTO(apiKeyValidationInfoDTO);

            tokenValidationContext.setAccessToken(jwtToken.getToken());
            tokenValidationContext.setHttpVerb(httpMethod);
            tokenValidationContext.setMatchingResource(matchingResource);
            tokenValidationContext.setContext(apiContext);
            tokenValidationContext.setVersion(apiVersion);

            boolean valid = ReferenceHolder.getInstance().getKeyValidationHandler(tenantDomain)
                    .validateScopes(tokenValidationContext);
            if (valid) {
                if (log.isDebugEnabled()) {
                    log.debug("Scope validation successful for the resource: " + matchingResource + ", user: "
                            + jwtValidationInfo.getUser());
                }
            } else {
                String message = "User is NOT authorized to access the Resource: " + matchingResource
                        + ". Scope validation failed.";
                log.debug(message);
                throw new APISecurityException(APISecurityConstants.INVALID_SCOPE, message);
            }
        } catch (MGWException e) {
            String message = "Error while accessing backend services for token scope validation";
            log.error(message, e);
            throw new APISecurityException(APISecurityConstants.API_AUTH_GENERAL_ERROR, message, e);
        }
    }

    private APIKeyValidationInfoDTO validateSubscriptionUsingKeyManager(RequestContext requestContext,
            JWTValidationInfo jwtValidationInfo) throws APISecurityException {

        String apiContext = requestContext.getMathedAPI().getAPIConfig().getBasePath();
        String apiVersion = requestContext.getMathedAPI().getAPIConfig().getVersion();
        return validateSubscriptionUsingKeyManager(apiContext, apiVersion, jwtValidationInfo);
    }

    private APIKeyValidationInfoDTO validateSubscriptionUsingKeyManager(String apiContext, String apiVersion,
            JWTValidationInfo jwtValidationInfo) throws APISecurityException {

        String tenantDomain = "carbon.super"; //TODO : get correct tenant domain

        String consumerKey = jwtValidationInfo.getConsumerKey();
        String keyManager = jwtValidationInfo.getKeyManager();
        if (consumerKey != null && keyManager != null) {
            return ReferenceHolder.getInstance().getKeyValidationHandler(tenantDomain)
                    .validateSubscription(apiContext, apiVersion, consumerKey, keyManager);
        }
        log.debug("Cannot call Key Manager to validate subscription. "
                + "Payload of the token does not contain the Authorized party - the party to which the ID Token was "
                + "issued");
        throw new APISecurityException(APISecurityConstants.API_AUTH_FORBIDDEN,
                APISecurityConstants.API_AUTH_FORBIDDEN_MESSAGE);
    }


    /**
     * Validate whether the user is subscribed to the invoked API. If subscribed, return a JSON object containing
     * the API information. This validation is done based on the jwt token claims.
     *
     * @param name API name
     * @param version API version
     * @param payload The payload of the JWT token
     * @return an JSON object containing subscribed API information retrieved from token payload.
     * If the subscription information is not found, return a null object.
     * @throws APISecurityException if the user is not subscribed to the API
     */
    private JSONObject validateSubscriptionFromClaim(String name, String version, JWTClaimsSet payload,
                                                     String[] splitToken, boolean isOauth)
            throws APISecurityException {
        JSONObject api = null;

        if (payload.getClaim(APIConstants.JwtTokenConstants.SUBSCRIBED_APIS) != null) {
            // Subscription validation
            JSONArray subscribedAPIs =
                    (JSONArray) payload.getClaim(APIConstants.JwtTokenConstants.SUBSCRIBED_APIS);
            for (int i = 0; i < subscribedAPIs.size(); i++) {
                JSONObject subscribedAPI =
                        (JSONObject) subscribedAPIs.get(i);
                if (name.equals(subscribedAPI.getAsString(APIConstants.JwtTokenConstants.API_NAME)) &&
                        version.equals(subscribedAPI.getAsString(APIConstants.JwtTokenConstants.API_VERSION)
                        )) {
                    api = subscribedAPI;
                    if (log.isDebugEnabled()) {
                        log.debug("User is subscribed to the API: " + name + ", " +
                                "version: " + version + ". Token: " + FilterUtils.getMaskedToken(splitToken[0]));
                    }
                    break;
                }
            }
            if (api == null) {
                if (log.isDebugEnabled()) {
                    log.debug("User is not subscribed to access the API: " + name +
                            ", version: " + version + ". Token: " + FilterUtils.getMaskedToken(splitToken[0]));
                }
                log.error("User is not subscribed to access the API.");
                throw new APISecurityException(APISecurityConstants.API_AUTH_FORBIDDEN,
                        APISecurityConstants.API_AUTH_FORBIDDEN_MESSAGE);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("No subscription information found in the token.");
            }
            // we perform mandatory authentication for Api Keys
            if (!isOauth) {
                log.error("User is not subscribed to access the API.");
                throw new APISecurityException(APISecurityConstants.API_AUTH_FORBIDDEN,
                        APISecurityConstants.API_AUTH_FORBIDDEN_MESSAGE);
            }
        }
        return api;
    }

    private JWTValidationInfo getJwtValidationInfo(SignedJWTInfo signedJWTInfo, String jti)
            throws APISecurityException {

        String jwtHeader = signedJWTInfo.getSignedJWT().getHeader().toString();
        String tenantDomain = "carbon.super"; //TODO : Get the tenant domain.
        JWTValidationInfo jwtValidationInfo = null;
        if (isGatewayTokenCacheEnabled) {
            String cacheToken = (String) CacheProvider.getGatewayTokenCache().getIfPresent(jti);
            if (cacheToken != null) {
                if (CacheProvider.getGatewayKeyCache().getIfPresent(jti) != null) {
                    JWTValidationInfo tempJWTValidationInfo = (JWTValidationInfo) CacheProvider.getGatewayKeyCache()
                            .getIfPresent(jti);
                    checkTokenExpiration(jti, tempJWTValidationInfo);
                    jwtValidationInfo = tempJWTValidationInfo;
                }
            } else if (CacheProvider.getInvalidTokenCache().getIfPresent(jti) != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Token retrieved from the invalid token cache. Token: "
                            + FilterUtils.getMaskedToken(jwtHeader));
                }
                log.error("Invalid JWT token. " + FilterUtils.getMaskedToken(jwtHeader));

                jwtValidationInfo = new JWTValidationInfo();
                jwtValidationInfo.setValidationCode(APISecurityConstants.API_AUTH_INVALID_CREDENTIALS);
                jwtValidationInfo.setValid(false);
            }
        }
        if (jwtValidationInfo == null) {

            try {
                jwtValidationInfo = jwtValidator.validateJWTToken(signedJWTInfo);
                if (isGatewayTokenCacheEnabled) {
                    // Add token to tenant token cache
                    if (jwtValidationInfo.isValid()) {
                        CacheProvider.getGatewayTokenCache().put(jti, true);
                        CacheProvider.getGatewayKeyCache().put(jti, jwtValidationInfo);
                    } else {
                        CacheProvider.getInvalidTokenCache().put(jti, true);
                    }

                }
                return jwtValidationInfo;
            } catch (MGWException e) {
                throw new APISecurityException(APISecurityConstants.API_AUTH_GENERAL_ERROR,
                        APISecurityConstants.API_AUTH_GENERAL_ERROR_MESSAGE);
            }
        }
        return jwtValidationInfo;
    }

    /**
     * Check whether the jwt token is expired or not.
     *
     * @param tokenIdentifier The token Identifier of JWT.
     * @param payload         The payload of the JWT token
     * @return
     */
    private JWTValidationInfo checkTokenExpiration(String tokenIdentifier, JWTValidationInfo payload) {

        long timestampSkew = getTimeStampSkewInSeconds();

        Date now = new Date();
        Date exp = new Date(payload.getExpiryTime());
        if (!DateUtils.isAfter(exp, now, timestampSkew)) {
            if (isGatewayTokenCacheEnabled) {
                CacheProvider.getGatewayTokenCache().invalidate(tokenIdentifier);
                CacheProvider.getGatewayJWTTokenCache().invalidate(tokenIdentifier);
                CacheProvider.getInvalidTokenCache().put(tokenIdentifier, true);
            }
            payload.setValid(false);
            payload.setValidationCode(APISecurityConstants.API_AUTH_INVALID_CREDENTIALS);
            return payload;
        }
        return payload;
    }

    protected long getTimeStampSkewInSeconds() {
        //TODO : Read from config
        return 5;
    }

    private SignedJWTInfo getSignedJwt(String accessToken) throws ParseException {

        String signature = accessToken.split("\\.")[2];
        SignedJWTInfo signedJWTInfo;
        LoadingCache gatewaySignedJWTParseCache = CacheProvider.getGatewaySignedJWTParseCache();
        if (gatewaySignedJWTParseCache != null) {
            Object cachedEntry = gatewaySignedJWTParseCache.getIfPresent(accessToken);
            if (cachedEntry != null) {
                signedJWTInfo = (SignedJWTInfo) cachedEntry;
            } else {
                SignedJWT signedJWT = SignedJWT.parse(accessToken);
                JWTClaimsSet jwtClaimsSet = signedJWT.getJWTClaimsSet();
                signedJWTInfo = new SignedJWTInfo(accessToken, signedJWT, jwtClaimsSet);
                gatewaySignedJWTParseCache.put(signature, signedJWTInfo);
            }
        } else {
            SignedJWT signedJWT = SignedJWT.parse(accessToken);
            JWTClaimsSet jwtClaimsSet = signedJWT.getJWTClaimsSet();
            signedJWTInfo = new SignedJWTInfo(accessToken, signedJWT, jwtClaimsSet);
        }
        return signedJWTInfo;
    }
}
