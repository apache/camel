/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.kubernetes;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class KubernetesConfiguration {

    @UriPath
    @Metadata(required = "true")
    private String masterUrl;

    @UriParam(enums = "namespaces,services,replicationControllers,pods,persistentVolumes,persistentVolumesClaims,secrets,resourcesQuota,serviceAccounts,nodes,configMaps,builds,buildConfigs")
    @Metadata(required = "true")
    private String category;

    @UriParam
    private DefaultKubernetesClient kubernetesClient;

    @UriParam(label = "security", secret = true)
    private String username;

    @UriParam(label = "security", secret = true)
    private String password;

    @UriParam(label = "producer", enums = "listNamespaces,listNamespacesByLabels,getNamespace,createNamespace,deleteNamespace,"
            + "listServices,listServicesByLabels,getService,createService,"
            + "deleteService,listReplicationControllers,listReplicationControllersByLabels,getReplicationController,"
            + "createReplicationController,deleteReplicationController,scaleReplicationController,"
            + "listPods,listPodsByLabels,getPod,createPod,deletePod,listPersistentVolumes,"
            + "listPersistentVolumesByLabels,getPersistentVolume,listPersistentVolumesClaims,"
            + "listPersistentVolumesClaimsByLabels,"
            + "getPersistentVolumeClaim,createPersistentVolumeClaim,deletePersistentVolumeClaim,listSecrets,"
            + "listSecretsByLabels,getSecret,createSecret,deleteSecret,"
            + "listResourcesQuota,listResourcesQuotaByLabels,getResourceQuota,"
            + "createResourceQuota,deleteResourceQuota,listServiceAccounts,listServiceAccountsByLabels,"
            + "getServiceAccount,createServiceAccount,"
            + "deleteServiceAccount,listNodes,listNodesByLabels,getNode,listConfigMaps,"
            + "listConfigMapsByLabels,getConfigMap,createConfigMap,deleteConfigMap,listBuilds,listBuildsByLabels," 
            + "getBuild,listBuildConfigs,listBuildConfigsByLabels,getBuildConfig")
    private String operation;

    @UriParam
    private String apiVersion;

    @UriParam(label = "security", secret = true)
    private String caCertData;

    @UriParam(label = "security", secret = true)
    private String caCertFile;

    @UriParam(label = "security", secret = true)
    private String clientCertData;

    @UriParam(label = "security", secret = true)
    private String clientCertFile;

    @UriParam(label = "security", secret = true)
    private String clientKeyAlgo;

    @UriParam(label = "security", secret = true)
    private String clientKeyData;

    @UriParam(label = "security", secret = true)
    private String clientKeyFile;

    @UriParam(label = "security", secret = true)
    private String clientKeyPassphrase;

    @UriParam(label = "security", secret = true)
    private String oauthToken;

    @UriParam(label = "security", secret = true)
    private Boolean trustCerts;

    @UriParam(label = "consumer")
    private String namespace;
    
    @UriParam(label = "consumer")
    private String labelKey;
    
    @UriParam(label = "consumer")
    private String labelValue;
    
    @UriParam(label = "consumer")
    private String resourceName;

    @UriParam
    private String portName;

    @UriParam
    private String dnsDomain;
    
    @UriParam(label = "consumer", defaultValue = "1")
    private int poolSize = 1;

    /**
     * Kubernetes Master url
     */
    public String getMasterUrl() {
        return masterUrl;
    }

    public void setMasterUrl(String masterUrl) {
        this.masterUrl = masterUrl;
    }

    /**
     * Kubernetes Producer and Consumer category
     */
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * Default KubernetesClient to use if provided
     */
    public DefaultKubernetesClient getKubernetesClient() {
        return kubernetesClient;
    }

    public void setKubernetesClient(DefaultKubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    /**
     * Username to connect to Kubernetes
     */
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Password to connect to Kubernetes
     */
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Producer operation to do on Kubernetes
     */
    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    /**
     * The Kubernetes API Version to use
     */
    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    /**
     * The CA Cert Data
     */
    public String getCaCertData() {
        return caCertData;
    }

    public void setCaCertData(String caCertData) {
        this.caCertData = caCertData;
    }

    /**
     * The CA Cert File
     */
    public String getCaCertFile() {
        return caCertFile;
    }

    public void setCaCertFile(String caCertFile) {
        this.caCertFile = caCertFile;
    }

    /**
     * The Client Cert Data
     */
    public String getClientCertData() {
        return clientCertData;
    }

    public void setClientCertData(String clientCertData) {
        this.clientCertData = clientCertData;
    }

    /**
     * The Client Cert File
     */
    public String getClientCertFile() {
        return clientCertFile;
    }

    public void setClientCertFile(String clientCertFile) {
        this.clientCertFile = clientCertFile;
    }

    /**
     * The Key Algorithm used by the client
     */
    public String getClientKeyAlgo() {
        return clientKeyAlgo;
    }

    public void setClientKeyAlgo(String clientKeyAlgo) {
        this.clientKeyAlgo = clientKeyAlgo;
    }

    /**
     * The Client Key data
     */
    public String getClientKeyData() {
        return clientKeyData;
    }

    public void setClientKeyData(String clientKeyData) {
        this.clientKeyData = clientKeyData;
    }

    /**
     * The Client Key file
     */
    public String getClientKeyFile() {
        return clientKeyFile;
    }

    public void setClientKeyFile(String clientKeyFile) {
        this.clientKeyFile = clientKeyFile;
    }

    /**
     * The Client Key Passphrase
     */
    public String getClientKeyPassphrase() {
        return clientKeyPassphrase;
    }

    public void setClientKeyPassphrase(String clientKeyPassphrase) {
        this.clientKeyPassphrase = clientKeyPassphrase;
    }

    /**
     * The Auth Token
     */
    public String getOauthToken() {
        return oauthToken;
    }

    public void setOauthToken(String oauthToken) {
        this.oauthToken = oauthToken;
    }

    /**
     * Define if the certs we used are trusted anyway or not
     */
    public Boolean getTrustCerts() {
        return trustCerts;
    }

    public void setTrustCerts(Boolean trustCerts) {
        this.trustCerts = trustCerts;
    }

    /**
     * The namespace
     */
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getPortName() {
        return portName;
    }

    /**
     * The port name, used for ServiceCall EIP
     */
    public void setPortName(String portName) {
        this.portName = portName;
    }

    public String getDnsDomain() {
        return dnsDomain;
    }

    /**
     * The dns domain, used for ServiceCall EIP
     */
    public void setDnsDomain(String dnsDomain) {
        this.dnsDomain = dnsDomain;
    }

    /**
     * @deprecated use {@link #getNamespace()}
     */
    @Deprecated
    public String getNamespaceName() {
        return getNamespace();
    }

    /**
     * @deprecated use {@link #setNamespace(String)}
     */
    @Deprecated
    public void setNamespaceName(String namespace) {
        setNamespace(namespace);
    }

    /**
     * The Consumer pool size
     */
    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }
   
    /**
     * The Consumer Label key when watching at some resources
     */
    public String getLabelKey() {
        return labelKey;
    }

    public void setLabelKey(String labelKey) {
        this.labelKey = labelKey;
    }

    /**
     * The Consumer Label value when watching at some resources
     */
    public String getLabelValue() {
        return labelValue;
    }

    public void setLabelValue(String labelValue) {
        this.labelValue = labelValue;
    }
    

    /**
     * The Consumer Resource Name we would like to watch
     */
    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    @Override
    public String toString() {
        return "KubernetesConfiguration [masterUrl=" + masterUrl + ", category=" + category + ", kubernetesClient="
                + kubernetesClient + ", username=" + username + ", password=" + password + ", operation=" + operation
                + ", apiVersion=" + apiVersion + ", caCertData=" + caCertData + ", caCertFile=" + caCertFile
                + ", clientCertData=" + clientCertData + ", clientCertFile=" + clientCertFile + ", clientKeyAlgo="
                + clientKeyAlgo + ", clientKeyData=" + clientKeyData + ", clientKeyFile=" + clientKeyFile
                + ", clientKeyPassphrase=" + clientKeyPassphrase + ", oauthToken=" + oauthToken + ", trustCerts="
                + trustCerts + ", namespace=" + namespace + ", labelKey=" + labelKey + ", labelValue=" + labelValue
                + ", resourceName=" + resourceName + ", portName=" + portName + ", dnsDomain=" + dnsDomain
                + ", poolSize=" + poolSize + "]";
    }

}
