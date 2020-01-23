package org.apache.camel.builder.component.dsl;

import org.apache.camel.Component;
import org.apache.camel.builder.component.AbstractComponentBuilder;
import org.apache.camel.builder.component.ComponentBuilder;
import org.apache.camel.component.salesforce.SalesforceComponent;

public interface SalesforceComponentBuilderFactory {

    static SalesforceComponentBuilder salesforce() {
        return new SalesforceComponentBuilderImpl();
    }

    interface SalesforceComponentBuilder extends ComponentBuilder {
        default SalesforceComponentBuilder withComponentName(String name) {
            doSetComponentName(name);
            return this;
        }
        default SalesforceComponentBuilder setAuthenticationType(
                org.apache.camel.component.salesforce.AuthenticationType authenticationType) {
            doSetProperty("authenticationType", authenticationType);
            return this;
        }
        default SalesforceComponentBuilder setLoginConfig(
                org.apache.camel.component.salesforce.SalesforceLoginConfig loginConfig) {
            doSetProperty("loginConfig", loginConfig);
            return this;
        }
        default SalesforceComponentBuilder setInstanceUrl(
                java.lang.String instanceUrl) {
            doSetProperty("instanceUrl", instanceUrl);
            return this;
        }
        default SalesforceComponentBuilder setLoginUrl(java.lang.String loginUrl) {
            doSetProperty("loginUrl", loginUrl);
            return this;
        }
        default SalesforceComponentBuilder setClientId(java.lang.String clientId) {
            doSetProperty("clientId", clientId);
            return this;
        }
        default SalesforceComponentBuilder setClientSecret(
                java.lang.String clientSecret) {
            doSetProperty("clientSecret", clientSecret);
            return this;
        }
        default SalesforceComponentBuilder setKeystore(
                org.apache.camel.support.jsse.KeyStoreParameters keystore) {
            doSetProperty("keystore", keystore);
            return this;
        }
        default SalesforceComponentBuilder setRefreshToken(
                java.lang.String refreshToken) {
            doSetProperty("refreshToken", refreshToken);
            return this;
        }
        default SalesforceComponentBuilder setUserName(java.lang.String userName) {
            doSetProperty("userName", userName);
            return this;
        }
        default SalesforceComponentBuilder setPassword(java.lang.String password) {
            doSetProperty("password", password);
            return this;
        }
        default SalesforceComponentBuilder setLazyLogin(boolean lazyLogin) {
            doSetProperty("lazyLogin", lazyLogin);
            return this;
        }
        default SalesforceComponentBuilder setConfig(
                org.apache.camel.component.salesforce.SalesforceEndpointConfig config) {
            doSetProperty("config", config);
            return this;
        }
        default SalesforceComponentBuilder setHttpClientProperties(
                java.util.Map<java.lang.String,java.lang.Object> httpClientProperties) {
            doSetProperty("httpClientProperties", httpClientProperties);
            return this;
        }
        default SalesforceComponentBuilder setLongPollingTransportProperties(
                java.util.Map<java.lang.String,java.lang.Object> longPollingTransportProperties) {
            doSetProperty("longPollingTransportProperties", longPollingTransportProperties);
            return this;
        }
        default SalesforceComponentBuilder setSslContextParameters(
                org.apache.camel.support.jsse.SSLContextParameters sslContextParameters) {
            doSetProperty("sslContextParameters", sslContextParameters);
            return this;
        }
        default SalesforceComponentBuilder setUseGlobalSslContextParameters(
                boolean useGlobalSslContextParameters) {
            doSetProperty("useGlobalSslContextParameters", useGlobalSslContextParameters);
            return this;
        }
        default SalesforceComponentBuilder setHttpClientIdleTimeout(
                long httpClientIdleTimeout) {
            doSetProperty("httpClientIdleTimeout", httpClientIdleTimeout);
            return this;
        }
        default SalesforceComponentBuilder setHttpClientConnectionTimeout(
                long httpClientConnectionTimeout) {
            doSetProperty("httpClientConnectionTimeout", httpClientConnectionTimeout);
            return this;
        }
        default SalesforceComponentBuilder setHttpMaxContentLength(
                java.lang.Integer httpMaxContentLength) {
            doSetProperty("httpMaxContentLength", httpMaxContentLength);
            return this;
        }
        default SalesforceComponentBuilder setHttpProxyHost(
                java.lang.String httpProxyHost) {
            doSetProperty("httpProxyHost", httpProxyHost);
            return this;
        }
        default SalesforceComponentBuilder setHttpProxyPort(
                java.lang.Integer httpProxyPort) {
            doSetProperty("httpProxyPort", httpProxyPort);
            return this;
        }
        default SalesforceComponentBuilder setHttpProxyUsername(
                java.lang.String httpProxyUsername) {
            doSetProperty("httpProxyUsername", httpProxyUsername);
            return this;
        }
        default SalesforceComponentBuilder setHttpProxyPassword(
                java.lang.String httpProxyPassword) {
            doSetProperty("httpProxyPassword", httpProxyPassword);
            return this;
        }
        default SalesforceComponentBuilder setIsHttpProxySocks4(
                boolean isHttpProxySocks4) {
            doSetProperty("isHttpProxySocks4", isHttpProxySocks4);
            return this;
        }
        default SalesforceComponentBuilder setIsHttpProxySecure(
                boolean isHttpProxySecure) {
            doSetProperty("isHttpProxySecure", isHttpProxySecure);
            return this;
        }
        default SalesforceComponentBuilder setHttpProxyIncludedAddresses(
                java.util.Set<java.lang.String> httpProxyIncludedAddresses) {
            doSetProperty("httpProxyIncludedAddresses", httpProxyIncludedAddresses);
            return this;
        }
        default SalesforceComponentBuilder setHttpProxyExcludedAddresses(
                java.util.Set<java.lang.String> httpProxyExcludedAddresses) {
            doSetProperty("httpProxyExcludedAddresses", httpProxyExcludedAddresses);
            return this;
        }
        default SalesforceComponentBuilder setHttpProxyAuthUri(
                java.lang.String httpProxyAuthUri) {
            doSetProperty("httpProxyAuthUri", httpProxyAuthUri);
            return this;
        }
        default SalesforceComponentBuilder setHttpProxyRealm(
                java.lang.String httpProxyRealm) {
            doSetProperty("httpProxyRealm", httpProxyRealm);
            return this;
        }
        default SalesforceComponentBuilder setHttpProxyUseDigestAuth(
                boolean httpProxyUseDigestAuth) {
            doSetProperty("httpProxyUseDigestAuth", httpProxyUseDigestAuth);
            return this;
        }
        default SalesforceComponentBuilder setPackages(
                java.lang.String[] packages) {
            doSetProperty("packages", packages);
            return this;
        }
        default SalesforceComponentBuilder setBasicPropertyBinding(
                boolean basicPropertyBinding) {
            doSetProperty("basicPropertyBinding", basicPropertyBinding);
            return this;
        }
        default SalesforceComponentBuilder setLazyStartProducer(
                boolean lazyStartProducer) {
            doSetProperty("lazyStartProducer", lazyStartProducer);
            return this;
        }
        default SalesforceComponentBuilder setBridgeErrorHandler(
                boolean bridgeErrorHandler) {
            doSetProperty("bridgeErrorHandler", bridgeErrorHandler);
            return this;
        }
    }

    class SalesforceComponentBuilderImpl
            extends
                AbstractComponentBuilder
            implements
                SalesforceComponentBuilder {
        public SalesforceComponentBuilderImpl() {
            super("salesforce");
        }
        @Override
        protected Component buildConcreteComponent() {
            return new SalesforceComponent();
        }
    }
}