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
package org.apache.camel.model.cloud;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

@Metadata(label = "routing,cloud,service-discovery")
@XmlRootElement(name = "kubernetesServiceDiscovery")
@XmlAccessorType(XmlAccessType.FIELD)
public class KubernetesServiceCallServiceDiscoveryConfiguration extends ServiceCallServiceDiscoveryConfiguration {
    @XmlAttribute
    @Metadata(defaultValue = "environment", enums = "environment,dns,client")
    private String lookup = "environment";
    @XmlAttribute
    @Metadata(label = "dns,dnssrv")
    private String dnsDomain;
    @XmlAttribute
    @Metadata(label = "dns,dnssrv")
    private String portName;
    @XmlAttribute
    @Metadata(label = "dns,dnssrv")
    private String portProtocol = "tcp";
    @XmlAttribute
    private String namespace;
    @XmlAttribute
    private String apiVersion;
    @XmlAttribute
    @Metadata(label = "client")
    private String masterUrl;
    @XmlAttribute
    @Metadata(label = "client")
    private String username;
    @XmlAttribute
    @Metadata(label = "client")
    private String password;
    @XmlAttribute
    @Metadata(label = "client")
    private String oauthToken;
    @XmlAttribute
    @Metadata(label = "client")
    private String caCertData;
    @XmlAttribute
    @Metadata(label = "client")
    private String caCertFile;
    @XmlAttribute
    @Metadata(label = "client")
    private String clientCertData;
    @XmlAttribute
    @Metadata(label = "client")
    private String clientCertFile;
    @XmlAttribute
    @Metadata(label = "client")
    private String clientKeyAlgo;
    @XmlAttribute
    @Metadata(label = "client")
    private String clientKeyData;
    @XmlAttribute
    @Metadata(label = "client")
    private String clientKeyFile;
    @XmlAttribute
    @Metadata(label = "client")
    private String clientKeyPassphrase;
    @XmlAttribute
    @Metadata(label = "client", javaType = "java.lang.Boolean")
    private String trustCerts;

    public KubernetesServiceCallServiceDiscoveryConfiguration() {
        this(null);
    }

    public KubernetesServiceCallServiceDiscoveryConfiguration(ServiceCallDefinition parent) {
        super(parent, "kubernetes-service-discovery");
    }

    // *************************************************************************
    // Properties
    // *************************************************************************

    public String getMasterUrl() {
        return masterUrl;
    }

    /**
     * Sets the URL to the master when using client lookup
     */
    public void setMasterUrl(String masterUrl) {
        this.masterUrl = masterUrl;
    }

    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets the namespace to use. Will by default use namespace from the ENV
     * variable KUBERNETES_MASTER.
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    /**
     * Sets the API version when using client lookup
     */
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getLookup() {
        return lookup;
    }

    /**
     * How to perform service lookup. Possible values: client, dns, environment.
     * <p/>
     * When using client, then the client queries the kubernetes master to
     * obtain a list of active pods that provides the service, and then random
     * (or round robin) select a pod.
     * <p/>
     * When using dns the service name is resolved as
     * <tt>name.namespace.svc.dnsDomain</tt>.
     * <p/>
     * When using dnssrv the service name is resolved with SRV query for
     * <tt>_<port_name>._<port_proto>.<serviceName>.<namespace>.svc.<zone>.</tt>.
     * <p/>
     * When using environment then environment variables are used to lookup the
     * service.
     * <p/>
     * By default environment is used.
     */
    public void setLookup(String lookup) {
        this.lookup = lookup;
    }

    public String getDnsDomain() {
        return dnsDomain;
    }

    /**
     * Sets the DNS domain to use for DNS lookup.
     */
    public void setDnsDomain(String dnsDomain) {
        this.dnsDomain = dnsDomain;
    }

    public String getPortName() {
        return portName;
    }

    /**
     * Sets the Port Name to use for DNS/DNSSRV lookup.
     */
    public void setPortName(String portName) {
        this.portName = portName;
    }

    public String getPortProtocol() {
        return portProtocol;
    }

    /**
     * Sets the Port Protocol to use for DNS/DNSSRV lookup.
     */
    public void setPortProtocol(String portProtocol) {
        this.portProtocol = portProtocol;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Sets the username for authentication when using client lookup
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Sets the password for authentication when using client lookup
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getOauthToken() {
        return oauthToken;
    }

    /**
     * Sets the OAUTH token for authentication (instead of username/password)
     * when using client lookup
     */
    public void setOauthToken(String oauthToken) {
        this.oauthToken = oauthToken;
    }

    public String getCaCertData() {
        return caCertData;
    }

    /**
     * Sets the Certificate Authority data when using client lookup
     */
    public void setCaCertData(String caCertData) {
        this.caCertData = caCertData;
    }

    public String getCaCertFile() {
        return caCertFile;
    }

    /**
     * Sets the Certificate Authority data that are loaded from the file when
     * using client lookup
     */
    public void setCaCertFile(String caCertFile) {
        this.caCertFile = caCertFile;
    }

    public String getClientCertData() {
        return clientCertData;
    }

    /**
     * Sets the Client Certificate data when using client lookup
     */
    public void setClientCertData(String clientCertData) {
        this.clientCertData = clientCertData;
    }

    public String getClientCertFile() {
        return clientCertFile;
    }

    /**
     * Sets the Client Certificate data that are loaded from the file when using
     * client lookup
     */
    public void setClientCertFile(String clientCertFile) {
        this.clientCertFile = clientCertFile;
    }

    public String getClientKeyAlgo() {
        return clientKeyAlgo;
    }

    /**
     * Sets the Client Keystore algorithm, such as RSA when using client lookup
     */
    public void setClientKeyAlgo(String clientKeyAlgo) {
        this.clientKeyAlgo = clientKeyAlgo;
    }

    public String getClientKeyData() {
        return clientKeyData;
    }

    /**
     * Sets the Client Keystore data when using client lookup
     */
    public void setClientKeyData(String clientKeyData) {
        this.clientKeyData = clientKeyData;
    }

    public String getClientKeyFile() {
        return clientKeyFile;
    }

    /**
     * Sets the Client Keystore data that are loaded from the file when using
     * client lookup
     */
    public void setClientKeyFile(String clientKeyFile) {
        this.clientKeyFile = clientKeyFile;
    }

    public String getClientKeyPassphrase() {
        return clientKeyPassphrase;
    }

    /**
     * Sets the Client Keystore passphrase when using client lookup
     */
    public void setClientKeyPassphrase(String clientKeyPassphrase) {
        this.clientKeyPassphrase = clientKeyPassphrase;
    }

    public String getTrustCerts() {
        return trustCerts;
    }

    /**
     * Sets whether to turn on trust certificate check when using client lookup
     */
    public void setTrustCerts(String trustCerts) {
        this.trustCerts = trustCerts;
    }

    // *************************************************************************
    // Fluent API
    // *************************************************************************

    /**
     * Sets the URL to the master when using client lookup
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration masterUrl(String masterUrl) {
        setMasterUrl(masterUrl);
        return this;
    }

    /**
     * Sets the namespace to use. Will by default use namespace from the ENV
     * variable KUBERNETES_MASTER.
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration namespace(String namespace) {
        setNamespace(namespace);
        return this;
    }

    /**
     * Sets the API version when using client lookup
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration apiVersion(String apiVersion) {
        setApiVersion(apiVersion);
        return this;
    }

    /**
     * How to perform service lookup, @see {@link #setLookup(String)}.
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration lookup(String lookup) {
        setLookup(lookup);
        return this;
    }

    /**
     * Sets the DNS domain to use for DNS/SNDSRV lookup.
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration dnsDomain(String dnsDomain) {
        setDnsDomain(dnsDomain);
        return this;
    }

    /**
     * Sets Port Name to use for DNS/SNDSRV lookup.
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration portName(String portName) {
        setPortName(portName);
        return this;
    }

    /**
     * Sets Port Protocol to use for DNS/SNDSRV lookup.
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration portProtocol(String portProtocol) {
        setPortProtocol(portProtocol);
        return this;
    }

    /**
     * Sets the username for authentication when using client lookup
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration username(String username) {
        setUsername(username);
        return this;
    }

    /**
     * Sets the password for authentication when using client lookup
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration password(String password) {
        setPassword(password);
        return this;
    }

    /**
     * Sets the OAUTH token for authentication (instead of username/password)
     * when using client lookup
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration oauthToken(String oauthToken) {
        setOauthToken(oauthToken);
        return this;
    }

    /**
     * Sets the Certificate Authority data when using client lookup
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration caCertData(String caCertData) {
        setCaCertData(caCertData);
        return this;
    }

    /**
     * Sets the Certificate Authority data that are loaded from the file when
     * using client lookup
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration caCertFile(String caCertFile) {
        setCaCertFile(caCertFile);
        return this;
    }

    /**
     * Sets the Client Certificate data when using client lookup
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration clientCertData(String clientCertData) {
        setClientCertData(clientCertData);
        return this;
    }

    /**
     * Sets the Client Certificate data that are loaded from the file when using
     * client lookup
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration clientCertFile(String clientCertFile) {
        setClientCertFile(clientCertFile);
        return this;
    }

    /**
     * Sets the Client Keystore algorithm, such as RSA when using client lookup
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration clientKeyAlgo(String clientKeyAlgo) {
        setClientKeyAlgo(clientKeyAlgo);
        return this;
    }

    /**
     * Sets the Client Keystore data when using client lookup
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration clientKeyData(String clientKeyData) {
        setClientKeyData(clientKeyData);
        return this;
    }

    /**
     * Sets the Client Keystore data that are loaded from the file when using
     * client lookup
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration clientKeyFile(String clientKeyFile) {
        setClientKeyFile(clientKeyFile);
        return this;
    }

    /**
     * Sets the Client Keystore passphrase when using client lookup
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration clientKeyPassphrase(String clientKeyPassphrase) {
        setClientKeyPassphrase(clientKeyPassphrase);
        return this;
    }

    /**
     * Sets whether to turn on trust certificate check when using client lookup
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration trustCerts(boolean trustCerts) {
        return trustCerts(Boolean.toString(trustCerts));
    }

    /**
     * Sets whether to turn on trust certificate check when using client lookup
     */
    public KubernetesServiceCallServiceDiscoveryConfiguration trustCerts(String trustCerts) {
        setTrustCerts(trustCerts);
        return this;
    }
}
