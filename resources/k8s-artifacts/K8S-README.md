# WSO2 API Microgateway deployment in Kubernetes

There are two modes when deploying Microgateway in Kubernetes.
   - Deployment without WSO2 API operator for kubernetes
   - Deployment with WSO2 API operator for kubernetes

When deploying the Microgateway in kubernetes there are 3 pods for the components as follows.
   - mg-router
   - mg-adapter
   - mg-enforcer
   
Router and Adapter services are exposed as NodePort type in kubernetes. Enforcer service is exposed as ClusterIP type.

## Microgateway quick start

*Prerequisites*
- [Kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/)

- [Kubernetes v1.14 or above](https://Kubernetes.io/docs/setup/) <br>

    - Minimum CPU : 2vCPU
    - Minimum Memory : 2GB
    
### Deployment without WSO2 API Operator for kubernetes

Let's host our first API on a Microgateway in kubernetes. We will be exposing the publicly available [petstore services](https://petstore.swagger.io/) via  microgateway in kubernetes

1. First download the CLI tool(APICTL) and the microgateway distribution from the  
[github release page](https://github.com/wso2/product-microgateway/releases)
and extract them to a folder of your choice.
  * [CLI (APICTL)](https://github.com/wso2/product-microgateway/releases)
  * [Microgateway Distribution](https://github.com/wso2/product-microgateway/releases)
 
  
CLI tool extracted location will be referred as `CLI_HOME` and Microgateway distribution extracted location would be 
referred as `MG_HOME`.

2. Using your command line client tool add the 'CLI_HOME' folder to your PATH variable.
```
export PATH=$PATH:<CLI_HOME>
```
3. Let's deploy the microgateway in kubernetes.
  - In kubernetes artifacts there are 3 separate folders for each component.
  - In each folder config maps, deployment and service are included.
  - You can use the following command to deploy each microgateway component in kubernetes.
  ```
  kubectl apply -f mg-adapter/ -f mg-enforcer/ -f mg-router/
  ```
  - You can verify the deployments using the following commands.
  ```
    kubectl get pods
    kubectl get services
  ```

4. Let's create our first project with name "petstore" by adding the [open API definition](https://petstore.swagger.io/v2/swagger.json) of the petstore . You can do that by executing the following command using your command line tool.
```
apictl init petstore --oas https://petstore.swagger.io/v2/swagger.json
```

5. The project is now initialized. You should notice a directory with name "petstore" being created in the location 
where you executed the command. 

6. As we have exposed the adapter service and router service in Node Port type, you can use the IP address of any Kubernetes node.
  - For Docker for Mac use "127.0.0.1" for the K8s node IP
  - For Minikube, use minikube ip command to get the K8s node IP
  - For GKE
       ```$xslt
           (apictl get nodes -o jsonpath='{ $.items[*].status.addresses[?(@.type=="ExternalIP")].address }')
       ```
       - This will give the external IPs of the nodes available in the cluster. Pick any IP from there.

7. Now let's deploy our first API to Microgateway in kubernetes using the project created in the step 4. Navigate to the location where the petstore project was initialized.
Execute the following command to deploy the API in kubernetes.

```
apictl mg deploy --host https://<*Node IP*>:30200 --file petstore  -u admin -p admin -k
```

The user credentials can be configured in the configurations of the `MG_HOME` distribution. `admin:admin` is the default accepted credentials by the 
microgateway adapter.

8. The next step would be to invoke the API using a REST tool. Since APIs on the Microgateway are by default secured. We need a valid token in order to invoke the API. 
Use the following sample token accepted by the microgateway to access the API. Lets set the token to command line as a variable

```
TOKEN=eyJ4NXQiOiJNell4TW1Ga09HWXdNV0kwWldObU5EY3hOR1l3WW1NNFpUQTNNV0kyTkRBelpHUXpOR00wWkdSbE5qSmtPREZrWkRSaU9URmtNV0ZoTXpVMlpHVmxOZyIsImtpZCI6Ik16WXhNbUZrT0dZd01XSTBaV05tTkRjeE5HWXdZbU00WlRBM01XSTJOREF6WkdRek5HTTBaR1JsTmpKa09ERmtaRFJpT1RGa01XRmhNelUyWkdWbE5nX1JTMjU2IiwiYWxnIjoiUlMyNTYifQ==.eyJhdWQiOiJBT2syNFF6WndRXzYyb2QyNDdXQnVtd0VFZndhIiwic3ViIjoiYWRtaW5AY2FyYm9uLnN1cGVyIiwibmJmIjoxNTk2MDA5NTU2LCJhenAiOiJBT2syNFF6WndRXzYyb2QyNDdXQnVtd0VFZndhIiwic2NvcGUiOiJhbV9hcHBsaWNhdGlvbl9zY29wZSBkZWZhdWx0IiwiaXNzIjoiaHR0cHM6Ly9sb2NhbGhvc3Q6OTQ0My9vYXV0aDIvdG9rZW4iLCJrZXl0eXBlIjoiUFJPRFVDVElPTiIsImV4cCI6MTYyNzU0NTU1NiwiaWF0IjoxNTk2MDA5NTU2LCJqdGkiOiIyN2ZkMWY4Ny01ZTI1LTQ1NjktYTJkYi04MDA3MTFlZTJjZWMifQ==.otDREOsUUmXuSbIVII7FR59HAWqtXh6WWCSX6NDylVIFfED3GbLkopo6rwCh2EX6yiP-vGTqX8sB9Zfn784cIfD3jz2hCZqOqNzSUrzamZrWui4hlYC6qt4YviMbR9LNtxxu7uQD7QMbpZQiJ5owslaASWQvFTJgBmss5t7cnurrfkatj5AkzVdKOTGxcZZPX8WrV_Mo2-rLbYMslgb2jCptgvi29VMPo9GlAFecoMsSwywL8sMyf7AJ3y4XW5Uzq7vDGxojDam7jI5W8uLVVolZPDstqqZYzxpPJ2hBFC_OZgWG3LqhUgsYNReDKKeWUIEieK7QPgjetOZ5Geb1mA==
``` 

9. We can now invoke the API running on the microgateway using cURL as below.
```
curl -X GET "https://<*Node IP*>:30201/v2/pet/1" -H "accept: application/json" -H "Authorization:Bearer $TOKEN" -k
```

### Deployment with WSO2 API Operator for kubernetes

The WSO2 API operator is running in the kubernetes cluster. This approach uses the kubernetes native way to deploy the APIS into a running microgateway cluster. 

Let's host our first API on a Microgateway in kubernetes using API Operator. We will be exposing the publicly available [petstore services](https://petstore.swagger.io/) via  microgateway in kubernetes

1. First download the microgateway distribution from the [github release page](https://github.com/wso2/product-microgateway/releases) and extract that to a folder of your choice. Microgateway distribution extracted location would be 
referred as `MG_HOME`.

2. Let's deploy the API Operator in the kubernetes cluster. Please refer [API Operator documentation](https://github.com/wso2/k8s-api-operator/tree/2.0.0) for detailed steps.

3. Let's deploy the microgateway in kubernetes.
  - In kubernetes artifacts there are 3 separate folders for each component.
  - In each folder config maps, deployment and service are included.
  - You can use the following commands to deploy each microgateway component in kubernetes.
  ```
  kubectl apply -f mg-adapter/ -f mg-enforcer/ -f mg-router/
  ```
  - You can verify the deployments using the following commands.
  ```
    kubectl get pods
    kubectl get services
  ```

4. As we have exposed the adapter service and router service in Node Port type, you can use the IP address of any Kubernetes node.
  - For Docker for Mac use "127.0.0.1" for the K8s node IP
  - For Minikube, use minikube ip command to get the K8s node IP
  - For GKE
       ```$xslt
           (apictl get nodes -o jsonpath='{ $.items[*].status.addresses[?(@.type=="ExternalIP")].address }')
       ```
       - This will give the external IPs of the nodes available in the cluster. Pick any IP from there.

5. Now let's create a config map either by providing a zip file of an API project or a swagger definition of an API.
  - Creating a config map using a zip file of an API project 
      ```
      kubectl create configmap petstore-cm --from-file=Petstore.zip
      ```
  - Creating a config map using a swagger definition of an API
      ```
      kubectl create configmap petstore-cm --from-file=swagger.json
      ```

6. After that create an api.yaml file with API Custom Resource Definition (CRD) pointing to the created config map.
```
apiVersion: wso2.com/v1alpha2
kind: API
metadata:
  name: petstore-api
spec:
  swaggerConfigMapName: petstore-cm
```
 - Apply the api.yaml file in the kubernetes cluster.
   ```
   kubectl apply -f api.yaml
   ```

7. The next step would be to invoke the API using a REST tool. Since APIs on the Microgateway are by default secured. We need a valid token in order to invoke the API. 
Use the following sample token accepted by the microgateway to access the API. Lets set the token to command line as a variable

```
TOKEN=eyJ4NXQiOiJNell4TW1Ga09HWXdNV0kwWldObU5EY3hOR1l3WW1NNFpUQTNNV0kyTkRBelpHUXpOR00wWkdSbE5qSmtPREZrWkRSaU9URmtNV0ZoTXpVMlpHVmxOZyIsImtpZCI6Ik16WXhNbUZrT0dZd01XSTBaV05tTkRjeE5HWXdZbU00WlRBM01XSTJOREF6WkdRek5HTTBaR1JsTmpKa09ERmtaRFJpT1RGa01XRmhNelUyWkdWbE5nX1JTMjU2IiwiYWxnIjoiUlMyNTYifQ==.eyJhdWQiOiJBT2syNFF6WndRXzYyb2QyNDdXQnVtd0VFZndhIiwic3ViIjoiYWRtaW5AY2FyYm9uLnN1cGVyIiwibmJmIjoxNTk2MDA5NTU2LCJhenAiOiJBT2syNFF6WndRXzYyb2QyNDdXQnVtd0VFZndhIiwic2NvcGUiOiJhbV9hcHBsaWNhdGlvbl9zY29wZSBkZWZhdWx0IiwiaXNzIjoiaHR0cHM6Ly9sb2NhbGhvc3Q6OTQ0My9vYXV0aDIvdG9rZW4iLCJrZXl0eXBlIjoiUFJPRFVDVElPTiIsImV4cCI6MTYyNzU0NTU1NiwiaWF0IjoxNTk2MDA5NTU2LCJqdGkiOiIyN2ZkMWY4Ny01ZTI1LTQ1NjktYTJkYi04MDA3MTFlZTJjZWMifQ==.otDREOsUUmXuSbIVII7FR59HAWqtXh6WWCSX6NDylVIFfED3GbLkopo6rwCh2EX6yiP-vGTqX8sB9Zfn784cIfD3jz2hCZqOqNzSUrzamZrWui4hlYC6qt4YviMbR9LNtxxu7uQD7QMbpZQiJ5owslaASWQvFTJgBmss5t7cnurrfkatj5AkzVdKOTGxcZZPX8WrV_Mo2-rLbYMslgb2jCptgvi29VMPo9GlAFecoMsSwywL8sMyf7AJ3y4XW5Uzq7vDGxojDam7jI5W8uLVVolZPDstqqZYzxpPJ2hBFC_OZgWG3LqhUgsYNReDKKeWUIEieK7QPgjetOZ5Geb1mA==
``` 

8. We can now invoke the API running on the microgateway using cURL as below.
```
curl -X GET "https://<*Node IP*>:30201/v2/pet/1" -H "accept: application/json" -H "Authorization:Bearer $TOKEN" -k
```

#### WSO2 API Microgateway APICTL commands

Following are the basic commands in APICTL which is used to deploy/update APIs in Microgateway

Note: Before you execute any of the commands below you need to add the path to the `<CLI_HOME` directory to the PATH environment variable. Ex: /home/dev/wso2am-micro-gw/bin

##### Init

`$ apictl init <project_name> --oas <filePathToOpenAPI_or_openAPIUrl`

The "apictl init" command is used to initialize a project structure with artifacts required to deploy API in Microgateway. This will create a **api_definitions**  directory.

Execute `apictl help init` to get more detailed information regarding the setup command.

Example

    $ apictl init petstore --oas https://petstore.swagger.io/v2/swagger.json

Let's see how we can expose the [petstore swagger](samples/petstore_swagger3.yaml) using the micro-gw.

##### Deploy

`$ apictl mg deploy --host <url_of_adaptor> --file <file_path_of_project_initiated_from_apictl>  --username <Username> --password <Password> -k`

Upon execution of this command, CLI tool deploy the API described with open API in the Microgateway.
```
 --host - Service url in which the Microgateway adapter is exposed.
 --file - File path of the project intitiated from apictl tool.
 --username - A valid username in order to communicate with the adapter (ex: admin)
 --password - The password of the user.
```
Example

	$ apictl mg deploy --host https://<*Node IP*>:30200 --file petstore.zip  --username admin --password admin


#### Invoke API exposed via microgateway
Once APIs are exposed we can invoke API with a valid jwt token or an opaque access token.
In order to use jwt tokens microgateway should be presented with  a jwt signed by a trusted OAuth2 service. There are few ways we can get a jwt token

1. Any third party secure token service
The public certificate of the token service which used to sign the token should be added to the trust store of the microgateway.
The jwt should have the claims **sub, aud, exp** in order to validate with microgateway

1. Get jwt from WSO2 API Manager
Please refer the [documentation](https://docs.wso2.com/display/AM260/Generate+a+JWT+token+from+the+API+Store) on how to get a valid jwt

The following sample command can be used to invoke the "/pet/findByStatus" resource of the petstore API

```
curl -X GET "https://<*Node IP*>:30201/petstore/v1/pet/findByStatus?status=available" -H "accept: application/xml" -H "Authorization:Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6Ik5UQXhabU14TkRNeVpEZzNNVFUxWkdNME16RXpPREpoWldJNE5ETmxaRFUxT0dGa05qRmlNUSJ9.eyJhdWQiOiJodHRwOlwvXC9vcmcud3NvMi5hcGltZ3RcL2dhdGV3YXkiLCJzdWIiOiJhZG1pbiIsImFwcGxpY2F0aW9uIjp7ImlkIjoyLCJuYW1lIjoiSldUX0FQUCIsInRpZXIiOiJVbmxpbWl0ZWQiLCJvd25lciI6ImFkbWluIn0sInNjb3BlIjoiYW1fYXBwbGljYXRpb25fc2NvcGUgZGVmYXVsdCIsImlzcyI6Imh0dHBzOlwvXC9sb2NhbGhvc3Q6OTQ0M1wvb2F1dGgyXC90b2tlbiIsImtleXR5cGUiOiJQUk9EVUNUSU9OIiwic3Vic2NyaWJlZEFQSXMiOltdLCJjb25zdW1lcktleSI6Ilg5TGJ1bm9oODNLcDhLUFAxbFNfcXF5QnRjY2EiLCJleHAiOjM3MDMzOTIzNTMsImlhdCI6MTU1NTkwODcwNjk2MSwianRpIjoiMjI0MTMxYzQtM2Q2MS00MjZkLTgyNzktOWYyYzg5MWI4MmEzIn0=.b_0E0ohoWpmX5C-M1fSYTkT9X4FN--_n7-bEdhC3YoEEk6v8So6gVsTe3gxC0VjdkwVyNPSFX6FFvJavsUvzTkq528mserS3ch-TFLYiquuzeaKAPrnsFMh0Hop6CFMOOiYGInWKSKPgI-VOBtKb1pJLEa3HvIxT-69X9CyAkwajJVssmo0rvn95IJLoiNiqzH8r7PRRgV_iu305WAT3cymtejVWH9dhaXqENwu879EVNFF9udMRlG4l57qa2AaeyrEguAyVtibAsO0Hd-DFy5MW14S6XSkZsis8aHHYBlcBhpy2RqcP51xRog12zOb-WcROy6uvhuCsv-hje_41WQ==" -k
```
Please note that the jwt provided in the command is a jwt token retrieved from WSO2 API Manager with higher expiry time which can be used with any API not protected with scopes.
This token works with any API since by default the microgateway config uses the public certificate of WSO2 API Manager to validate the signature.
