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
package org.apache.camel.component.braintree;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import com.braintreegateway.BraintreeGateway;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.braintree.internal.BraintreeApiCollection;
import org.apache.camel.component.braintree.internal.BraintreeApiName;
import org.apache.camel.util.component.AbstractApiComponent;

/**
 * Represents the component that manages {@link BraintreeEndpoint}.
 */
public class BraintreeComponent extends AbstractApiComponent<BraintreeApiName, BraintreeConfiguration, BraintreeApiCollection> {
    private final Map<String, BraintreeGateway> gateways;

    public BraintreeComponent() {
        super(BraintreeEndpoint.class, BraintreeApiName.class, BraintreeApiCollection.getCollection());
        this.gateways = new HashMap<>();
    }

    public BraintreeComponent(CamelContext context) {
        super(context, BraintreeEndpoint.class, BraintreeApiName.class, BraintreeApiCollection.getCollection());
        this.gateways = new HashMap<>();
    }

    @Override
    protected BraintreeApiName getApiName(String apiNameStr) throws IllegalArgumentException {
        return BraintreeApiName.fromValue(apiNameStr);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String methodName, BraintreeApiName apiName, BraintreeConfiguration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new BraintreeEndpoint(uri, this, apiName, methodName, endpointConfiguration, getGateway(endpointConfiguration));
    }

    /**
     * To use the shared configuration. Properties of the shared configuration can also be set individually.
     */
    @Override
    public void setConfiguration(BraintreeConfiguration configuration) {
        super.setConfiguration(configuration);
    }

    @Override
    public BraintreeConfiguration getConfiguration() {
        return super.getConfiguration();
    }

    private synchronized BraintreeGateway getGateway(BraintreeConfiguration configuration) {
        BraintreeGateway gateway = gateways.get(configuration.getMerchantId());
        if (gateway == null) {
            //TODO: review the key used to track gateways
            gateways.put(configuration.getMerchantId(), gateway = configuration.newBraintreeGateway());
        }

        return gateway;
    }

    private BraintreeConfiguration getConfigurationOrCreate() {
        if (this.getConfiguration() == null) {
            this.setConfiguration(new BraintreeConfiguration());
        }
        return this.getConfiguration();
    }

    public BraintreeApiName getApiName() {
        return getConfigurationOrCreate().getApiName();
    }

    /**
     * What kind of operation to perform
     * @param apiName
     */
    public void setApiName(BraintreeApiName apiName) {
        getConfigurationOrCreate().setApiName(apiName);
    }

    public String getMethodName() {
        return getConfigurationOrCreate().getMethodName();
    }

    /**
     * What sub operation to use for the selected operation
     * @param methodName
     */
    public void setMethodName(String methodName) {
        getConfigurationOrCreate().setMethodName(methodName);
    }

    public String getEnvironment() {
        return getConfigurationOrCreate().getEnvironment();
    }

    /**
     * The environment Either SANDBOX or PRODUCTION
     * @param environment
     */
    public void setEnvironment(String environment) {
        getConfigurationOrCreate().setEnvironment(environment);
    }

    public String getMerchantId() {
        return getConfigurationOrCreate().getMerchantId();
    }

    /**
     * The merchant id provided by Braintree.
     * @param merchantId
     */
    public void setMerchantId(String merchantId) {
        getConfigurationOrCreate().setMerchantId(merchantId);
    }

    public String getPublicKey() {
        return getConfigurationOrCreate().getPublicKey();
    }

    /**
     * The public key provided by Braintree.
     * @param publicKey
     */
    public void setPublicKey(String publicKey) {
        getConfigurationOrCreate().setPublicKey(publicKey);
    }

    public String getPrivateKey() {
        return getConfigurationOrCreate().getPrivateKey();
    }

    /**
     * The private key provided by Braintree.
     * @param privateKey
     */
    public void setPrivateKey(String privateKey) {
        getConfigurationOrCreate().setPrivateKey(privateKey);
    }

    public String getProxyHost() {
        return getConfigurationOrCreate().getProxyHost();
    }

    /**
     * The proxy host
     * @param proxyHost
     */
    public void setProxyHost(String proxyHost) {
        getConfigurationOrCreate().setProxyHost(proxyHost);
    }

    public Integer getProxyPort() {
        return getConfigurationOrCreate().getProxyPort();
    }

    /**
     * The proxy port
     * @param proxyPort
     */
    public void setProxyPort(Integer proxyPort) {
        getConfigurationOrCreate().setProxyPort(proxyPort);
    }

    public Level getHttpLogLevel() {
        return getConfigurationOrCreate().getHttpLogLevel();
    }

    /**
     * Set logging level for http calls, @see java.util.logging.Level
     * @param httpLogLevel
     */
    public void setHttpLogLevel(String httpLogLevel) {
        getConfigurationOrCreate().setHttpLogLevel(httpLogLevel);
    }

    /**
     * Set logging level for http calls, @see java.util.logging.Level
     * @param httpLogLevel
     */
    public void setHttpLogLevel(Level httpLogLevel) {
        getConfigurationOrCreate().setHttpLogLevel(httpLogLevel);
    }

    public String getHttpLogName() {
        return getConfigurationOrCreate().getHttpLogName();
    }

    /**
     * Set log category to use to log http calls, default "Braintree"
     * @param httpLogName
     */
    public void setHttpLogName(String httpLogName) {
        getConfigurationOrCreate().setHttpLogName(httpLogName);
    }

    public Integer getHttpReadTimeout() {
        return getConfigurationOrCreate().getHttpReadTimeout();
    }

    /**
     * Set read timeout for http calls.
     * @param httpReadTimeout
     */
    public void setHttpReadTimeout(Integer httpReadTimeout) {
        getConfigurationOrCreate().setHttpReadTimeout(httpReadTimeout);
    }
}
