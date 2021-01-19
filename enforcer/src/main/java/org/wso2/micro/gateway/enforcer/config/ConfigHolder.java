/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.gateway.enforcer.config;

import com.moandjiezana.toml.Toml;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wso2.micro.gateway.enforcer.config.dto.CredentialDto;
import org.wso2.micro.gateway.enforcer.config.dto.JWKSConfigurationDTO;
import org.wso2.micro.gateway.enforcer.config.dto.TokenIssuerDto;
import org.wso2.micro.gateway.enforcer.constants.ConfigConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Map;

/**
 * Configuration holder class for Microgateway.
 */
public class ConfigHolder {

    private static final Logger logger = LogManager.getLogger(ConfigHolder.class);

    private static ConfigHolder configHolder;
    private Toml configToml;
    EnforcerConfig config;
    private KeyStore trustStore = null;

    private ConfigHolder() {
        init();
    }

    public static ConfigHolder getInstance() {
        if (configHolder != null) {
            return configHolder;
        }
        configHolder = new ConfigHolder();
        return configHolder;
    }

    /**
     * Initialize the configuration provider class by reading the Mgw Configuration file.
     */
    private void init() {
        String home = System.getenv(ConfigConstants.ENFORCER_HOME);
        File file = new File(home + File.separator + ConfigConstants.CONF_DIR + File.separator + "config.toml");
        configToml = new Toml().read(file).getTable(ConfigConstants.CONF_ENFORCER_TABLE);
        config = configToml.to(EnforcerConfig.class);
        readUnparsableConfigs();
    }

    /**
     * {@link EnforcerConfig} object should match with the configurations defined, so that it automatically parse
     * the config file to the object. This method has only to be used when there are exceptions in which we can't
     * parse the config to object model using the toml parser.
     */
    private void readUnparsableConfigs() {
        //Load Client Trust Store
        loadTrustStore();

        // Read jwt token configuration
        populateJWTIssuerConfiguration();

        //Read credentials used to connect with APIM services
        populateAPIMCredentials();
    }

    private void populateJWTIssuerConfiguration()  {
        List<Object> jwtIssuers = configToml.getList(ConfigConstants.JWT_TOKEN_CONFIG);
        for (Object jwtIssuer : jwtIssuers) {
            Map<String, Object> issuer = (Map<String, Object>) jwtIssuer;
            TokenIssuerDto issuerDto = new TokenIssuerDto((String) issuer.get(ConfigConstants.JWT_TOKEN_ISSUER));

            JWKSConfigurationDTO jwksConfigurationDTO = new JWKSConfigurationDTO();
            jwksConfigurationDTO.setEnabled(StringUtils.isNotEmpty(
                    (String) issuer.get(ConfigConstants.JWT_TOKEN_JWKS_URL)));
            jwksConfigurationDTO.setUrl((String) issuer.get(ConfigConstants.JWT_TOKEN_JWKS_URL));
            issuerDto.setJwksConfigurationDTO(jwksConfigurationDTO);

            String certificateAlias = (String) issuer.get(ConfigConstants.JWT_TOKEN_CERTIFICATE_ALIAS);
            try {
                if (trustStore.getCertificate(certificateAlias) != null) {
                    Certificate issuerCertificate = trustStore.getCertificate(certificateAlias);
                    issuerDto.setCertificate(issuerCertificate);
                }
            } catch (KeyStoreException e) {
                logger.error("Error while loading certificate with alias " + certificateAlias + " from the trust store",
                        e);
            }

            issuerDto.setName((String) issuer.get(ConfigConstants.JWT_TOKEN_ISSUER_NAME));
            issuerDto.setConsumerKeyClaim((String) issuer.get(ConfigConstants.JWT_TOKEN_CONSUMER_KEY_CLAIM));
            issuerDto.setValidateSubscriptions((boolean) issuer.get(ConfigConstants.JWT_TOKEN_VALIDATE_SUBSCRIPTIONS));
            config.getIssuersMap().put((String) issuer.get(ConfigConstants.JWT_TOKEN_ISSUER), issuerDto);
        }
    }

    private void loadTrustStore() {
        String trustStoreLocation = configToml.getString(ConfigConstants.MGW_TRUST_STORE_LOCATION);
        String trustStorePassword = configToml.getString(ConfigConstants.MGW_TRUST_STORE_PASSWORD);;
        if (trustStoreLocation != null && trustStorePassword != null) {
            try {
                InputStream inputStream = new FileInputStream(new File(trustStoreLocation));
                trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                trustStore.load(inputStream, trustStorePassword.toCharArray());
            } catch (IOException | KeyStoreException | CertificateException | NoSuchAlgorithmException e) {
                logger.error("Error in loading trust store.", e);
            }
        } else {
            logger.error("Error in loading trust store. Configurations are not set.");
        }
    }

    private void populateAPIMCredentials() {
        String username = configToml.getString(ConfigConstants.APIM_CREDENTIAL_USERNAME);
        String password = configToml.getString(ConfigConstants.APIM_CREDENTIAL_PASSWORD);
        CredentialDto credentialDto = new CredentialDto(username, password.toCharArray());
        config.setApimCredentials(credentialDto);
    }

    public EnforcerConfig getConfig() {
        return config;
    }

    public void setConfig(EnforcerConfig config) {
        this.config = config;
    }

    public KeyStore getTrustStore() {
        return trustStore;
    }

}
