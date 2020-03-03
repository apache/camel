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
package org.apache.camel.component.braintree;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.Environment;
import org.apache.camel.component.braintree.internal.BraintreeApiName;
import org.apache.camel.component.braintree.internal.BraintreeLogHandler;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;

/**
 * Component configuration for Braintree component.
 */
@UriParams
public class BraintreeConfiguration {
    private static final String ENVIRONMENT = "environment";
    private static final String MERCHANT_ID = "merchant_id";
    private static final String PUBLIC_KEY  = "public_key";
    private static final String PRIVATE_KEY = "private_key";
    private static final String ACCESS_TOKEN = "access_token";

    @UriPath
    @Metadata(required = true)
    private BraintreeApiName apiName;
    @UriPath
    private String methodName;

    @UriParam
    private String environment;
    @UriParam
    private String merchantId;

    @UriParam(label = "security", secret = true)
    private String publicKey;
    @UriParam(label = "security", secret = true)
    private String privateKey;
    @UriParam(label = "security", secret = true)
    private String accessToken;

    @UriParam(label = "proxy")
    private String proxyHost;
    @UriParam(label = "proxy")
    private Integer proxyPort;

    @UriParam(label = "logging", javaType = "java.lang.String")
    private Level httpLogLevel;
    @UriParam(label = "logging", defaultValue = "Braintree")
    private String httpLogName;
    @UriParam(label = "logging", defaultValue = "true")
    private boolean logHandlerEnabled = true;

    @UriParam(label = "advanced")
    private Integer httpReadTimeout;

    public BraintreeApiName getApiName() {
        return apiName;
    }

    /**
     * What kind of operation to perform
     */
    public void setApiName(BraintreeApiName apiName) {
        this.apiName = apiName;
    }

    public String getMethodName() {
        return methodName;
    }

    /**
     * What sub operation to use for the selected operation
     */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getEnvironment() {
        return environment;
    }

    /**
     * The environment Either SANDBOX or PRODUCTION
     */
    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getMerchantId() {
        return merchantId;
    }

    /**
     * The merchant id provided by Braintree.
     */
    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getPublicKey() {
        return publicKey;
    }

    /**
     * The public key provided by Braintree.
     */
    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    /**
     * The private key provided by Braintree.
     */
    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getAccessToken() {
        return accessToken;
    }

    /**
     * The access token granted by a merchant to another in order to process transactions on their behalf.
     * Used in place of environment, merchant id, public key and private key fields.
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * The proxy host
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * The proxy port
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public Level getHttpLogLevel() {
        return httpLogLevel;
    }

    /**
     * Set logging level for http calls, @see java.util.logging.Level
     */
    public void setHttpLogLevel(String httpLogLevel) {
        this.httpLogLevel = Level.parse(httpLogLevel);
    }

    /**
     * Set logging level for http calls, @see java.util.logging.Level
     */
    public void setHttpLogLevel(Level httpLogLevel) {
        this.httpLogLevel = httpLogLevel;
    }

    public String getHttpLogName() {
        return httpLogName;
    }

    /**
     * Set log category to use to log http calls.
     */
    public void setHttpLogName(String httpLogName) {
        this.httpLogName = httpLogName;
    }

    public Integer getHttpReadTimeout() {
        return httpReadTimeout;
    }

    /**
     * Sets whether to enable the BraintreeLogHandler. It may be desirable to set this to
     * 'false' where an existing JUL - SLF4J logger bridge is on the classpath.
     *
     * This option can also be configured globally on the BraintreeComponent.
     */
    public void setLogHandlerEnabled(boolean logHandlerEnabled) {
        this.logHandlerEnabled = logHandlerEnabled;
    }

    public boolean isLogHandlerEnabled() {
        return logHandlerEnabled;
    }

    /**
     * Set read timeout for http calls.
     */
    public void setHttpReadTimeout(Integer httpReadTimeout) {
        this.httpReadTimeout = httpReadTimeout;
    }

    /**
     * Helper method to get and Environment object from its name
     */
    private Environment getBraintreeEnvironment() {
        String name = getEnvironment();
        if (ObjectHelper.equal(Environment.DEVELOPMENT.getEnvironmentName(), name, true)) {
            return Environment.DEVELOPMENT;
        }
        if (ObjectHelper.equal(Environment.SANDBOX.getEnvironmentName(), name, true)) {
            return Environment.SANDBOX;
        }
        if (ObjectHelper.equal(Environment.PRODUCTION.getEnvironmentName(), name, true)) {
            return Environment.PRODUCTION;
        }

        throw new IllegalArgumentException(String.format(
            "Environment should be development, sandbox or production, got %s", name));
    }

    /**
     * Construct a BraintreeGateway from configuration
     */
    synchronized BraintreeGateway newBraintreeGateway() {
        final BraintreeGateway gateway;

        if (accessToken != null) {
            gateway = new BraintreeGateway(
                    accessToken
            );
            setEnvironment(gateway.getConfiguration().getEnvironment().getEnvironmentName());
        } else {
            gateway = new BraintreeGateway(
                    getBraintreeEnvironment(),
                    getMerchantId(),
                    getPublicKey(),
                    getPrivateKey()
            );
        }

        if (ObjectHelper.isNotEmpty(proxyHost) && ObjectHelper.isNotEmpty(proxyPort)) {
            gateway.setProxy(proxyHost, proxyPort);
        }

        if (httpReadTimeout != null) {
            gateway.getConfiguration().setTimeout(httpReadTimeout);
        }

        // If custom log name is defined, a new logger wil be requested otherwise
        // the one supplied by Braintree' SDK will be used
        final Logger logger = ObjectHelper.isNotEmpty(httpLogName)
            ? Logger.getLogger(httpLogName)
            : gateway.getConfiguration().getLogger();

        // Cleanup handlers as by default braintree install a ConsoleHandler
        for (Handler handler : logger.getHandlers()) {
            logger.removeHandler(handler);
        }

        if (isLogHandlerEnabled()) {
            logger.addHandler(new BraintreeLogHandler());
        }

        if (httpLogLevel != null) {
            logger.setLevel(httpLogLevel);
        }

        gateway.getConfiguration().setLogger(logger);

        return gateway;
    }
}
