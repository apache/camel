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
package org.apache.camel.component.kubernetes.cloud;

import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.cloud.ServiceDiscoveryFactory;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.util.ObjectHelper;

public class KubernetesServiceDiscoveryFactory implements ServiceDiscoveryFactory {
    private final KubernetesConfiguration configuration;
    private String lookup;

    public KubernetesServiceDiscoveryFactory() {
        this(new KubernetesConfiguration());
    }

    public KubernetesServiceDiscoveryFactory(KubernetesConfiguration configuration) {
        this.configuration = configuration;
    }

    // *************************************************************************
    // Properties
    // *************************************************************************

    public String getMasterUrl() {
        return configuration.getMasterUrl();
    }

    public void setMasterUrl(String masterUrl) {
        configuration.setMasterUrl(masterUrl);
    }

    public String getUsername() {
        return configuration.getUsername();
    }

    public void setUsername(String username) {
        configuration.setUsername(username);
    }

    public String getPassword() {
        return configuration.getPassword();
    }

    public void setPassword(String password) {
        configuration.setPassword(password);
    }

    public String getApiVersion() {
        return configuration.getApiVersion();
    }

    public void setApiVersion(String apiVersion) {
        configuration.setApiVersion(apiVersion);
    }

    public String getCaCertData() {
        return configuration.getCaCertData();
    }

    public void setCaCertData(String caCertData) {
        configuration.setCaCertData(caCertData);
    }

    public String getCaCertFile() {
        return configuration.getCaCertFile();
    }

    public void setCaCertFile(String caCertFile) {
        configuration.setCaCertFile(caCertFile);
    }

    public String getClientCertData() {
        return configuration.getClientCertData();
    }

    public void setClientCertData(String clientCertData) {
        configuration.setClientCertData(clientCertData);
    }

    public String getClientCertFile() {
        return configuration.getClientCertFile();
    }

    public void setClientCertFile(String clientCertFile) {
        configuration.setClientCertFile(clientCertFile);
    }

    public String getClientKeyAlgo() {
        return configuration.getClientKeyAlgo();
    }

    public void setClientKeyAlgo(String clientKeyAlgo) {
        configuration.setClientKeyAlgo(clientKeyAlgo);
    }

    public String getClientKeyData() {
        return configuration.getClientKeyData();
    }

    public void setClientKeyData(String clientKeyData) {
        configuration.setClientKeyData(clientKeyData);
    }

    public String getClientKeyFile() {
        return configuration.getClientKeyFile();
    }

    public void setClientKeyFile(String clientKeyFile) {
        configuration.setClientKeyFile(clientKeyFile);
    }

    public String getClientKeyPassphrase() {
        return configuration.getClientKeyPassphrase();
    }

    public void setClientKeyPassphrase(String clientKeyPassphrase) {
        configuration.setClientKeyPassphrase(clientKeyPassphrase);
    }

    public String getOauthToken() {
        return configuration.getOauthToken();
    }

    public void setOauthToken(String oauthToken) {
        configuration.setOauthToken(oauthToken);
    }

    public Boolean getTrustCerts() {
        return configuration.getTrustCerts();
    }

    public void setTrustCerts(Boolean trustCerts) {
        configuration.setTrustCerts(trustCerts);
    }

    public String getNamespace() {
        return configuration.getNamespace();
    }

    public void setNamespace(String namespace) {
        configuration.setNamespace(namespace);
    }

    public String getDnsDomain() {
        return configuration.getDnsDomain();
    }

    public void setDnsDomain(String dnsDomain) {
        configuration.setDnsDomain(dnsDomain);
    }

    public String getLookup() {
        return lookup;
    }

    public void setLookup(String lookup) {
        this.lookup = lookup;
    }

    // *************************************************************************
    // Factory
    // *************************************************************************

    @Override
    public ServiceDiscovery newInstance(CamelContext camelContext) throws Exception {
        if (ObjectHelper.equal("dns", lookup)) {
            return new KubernetesDnsServiceDiscovery(configuration);
        } else if (ObjectHelper.equal("client", lookup)) {
            return new KubernetesClientServiceDiscovery(configuration);
        }

        return new KubernetesEnvServiceDiscovery(configuration);
    }
}
