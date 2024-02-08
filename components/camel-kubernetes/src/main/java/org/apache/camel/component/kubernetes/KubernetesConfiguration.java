/*
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

import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class KubernetesConfiguration implements Cloneable {

    @UriPath
    @Metadata(required = true)
    private String masterUrl;

    @Deprecated
    private String category;

    @UriParam
    private KubernetesClient kubernetesClient;

    @UriParam(label = "security", secret = true)
    private String username;

    @UriParam(label = "security", secret = true)
    private String password;

    @UriParam(label = "producer")
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

    @UriParam
    private String namespace;

    @UriParam(label = "consumer")
    private String labelKey;

    @UriParam(label = "consumer")
    private String labelValue;

    @UriParam(label = "consumer")
    private String resourceName;

    @UriParam
    private String portName;

    @UriParam(defaultValue = "tcp")
    private String portProtocol = "tcp";

    @UriParam
    private String dnsDomain;

    @UriParam(label = "consumer", defaultValue = "1")
    private int poolSize = 1;

    @UriParam(label = "advanced")
    private Integer connectionTimeout;

    @UriParam(label = "consumer")
    private String crdName;

    @UriParam(label = "consumer")
    private String crdGroup;

    @UriParam(label = "consumer")
    private String crdScope;

    @UriParam(label = "consumer")
    private String crdVersion;

    @UriParam(label = "consumer")
    private String crdPlural;

    /**
     * URL to a remote Kubernetes API server.
     *
     * This should only be used when your Camel application is connecting from outside Kubernetes. If you run your Camel
     * application inside Kubernetes, then you can use local or client as the URL to tell Camel to run in local mode.
     *
     * If you connect remotely to Kubernetes, then you may also need some of the many other configuration options for
     * secured connection with certificates, etc.
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
    public KubernetesClient getKubernetesClient() {
        return kubernetesClient;
    }

    public void setKubernetesClient(KubernetesClient kubernetesClient) {
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

    public String getPortProtocol() {
        return portProtocol;
    }

    /**
     * The port protocol, used for ServiceCall EIP
     */
    public void setPortProtocol(String portProtocol) {
        this.portProtocol = portProtocol;
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

    public Integer getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Connection timeout in milliseconds to use when making requests to the Kubernetes API server.
     */
    public void setConnectionTimeout(Integer connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * The Consumer CRD Resource name we would like to watch
     */
    public String getCrdName() {
        return crdName;
    }

    public void setCrdName(String crdName) {
        this.crdName = crdName;
    }

    /**
     * The Consumer CRD Resource Group we would like to watch
     */
    public String getCrdGroup() {
        return crdGroup;
    }

    public void setCrdGroup(String crdGroup) {
        this.crdGroup = crdGroup;
    }

    /**
     * The Consumer CRD Resource Scope we would like to watch
     */
    public String getCrdScope() {
        return crdScope;
    }

    public void setCrdScope(String crdScope) {
        this.crdScope = crdScope;
    }

    /**
     * The Consumer CRD Resource Version we would like to watch
     */
    public String getCrdVersion() {
        return crdVersion;
    }

    public void setCrdVersion(String crdVersion) {
        this.crdVersion = crdVersion;
    }

    /**
     * The Consumer CRD Resource Plural we would like to watch
     */
    public String getCrdPlural() {
        return crdPlural;
    }

    public void setCrdPlural(String crdPlural) {
        this.crdPlural = crdPlural;
    }

    // ****************************************
    // Copy
    // ****************************************

    public KubernetesConfiguration copy() {
        try {
            return (KubernetesConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    public String toString() {
        return "KubernetesConfiguration [masterUrl=" + masterUrl + ", category=" + category + ", kubernetesClient="
               + kubernetesClient + ", username=" + username + ", password="
               + password + ", operation=" + operation + ", apiVersion=" + apiVersion + ", caCertData=" + caCertData
               + ", caCertFile=" + caCertFile + ", clientCertData="
               + clientCertData + ", clientCertFile=" + clientCertFile + ", clientKeyAlgo=" + clientKeyAlgo + ", clientKeyData="
               + clientKeyData + ", clientKeyFile="
               + clientKeyFile + ", clientKeyPassphrase=" + clientKeyPassphrase + ", oauthToken=" + oauthToken + ", trustCerts="
               + trustCerts + ", namespace=" + namespace
               + ", labelKey=" + labelKey + ", labelValue=" + labelValue + ", resourceName=" + resourceName + ", portName="
               + portName + ", dnsDomain=" + dnsDomain + ", poolSize="
               + poolSize + ", connectionTimeout=" + connectionTimeout + "]";
    }

}
