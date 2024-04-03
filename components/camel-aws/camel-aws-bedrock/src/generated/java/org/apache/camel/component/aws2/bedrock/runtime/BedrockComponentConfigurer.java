/* Generated by camel build tools - do NOT edit this file! */
package org.apache.camel.component.aws2.bedrock.runtime;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ExtendedPropertyConfigurerGetter;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.spi.ConfigurerStrategy;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.apache.camel.util.CaseInsensitiveMap;
import org.apache.camel.support.component.PropertyConfigurerSupport;

/**
 * Generated by camel build tools - do NOT edit this file!
 */
@SuppressWarnings("unchecked")
public class BedrockComponentConfigurer extends PropertyConfigurerSupport implements GeneratedPropertyConfigurer, PropertyConfigurerGetter {

    private org.apache.camel.component.aws2.bedrock.runtime.BedrockConfiguration getOrCreateConfiguration(BedrockComponent target) {
        if (target.getConfiguration() == null) {
            target.setConfiguration(new org.apache.camel.component.aws2.bedrock.runtime.BedrockConfiguration());
        }
        return target.getConfiguration();
    }

    @Override
    public boolean configure(CamelContext camelContext, Object obj, String name, Object value, boolean ignoreCase) {
        BedrockComponent target = (BedrockComponent) obj;
        switch (ignoreCase ? name.toLowerCase() : name) {
        case "accesskey":
        case "accessKey": getOrCreateConfiguration(target).setAccessKey(property(camelContext, java.lang.String.class, value)); return true;
        case "autowiredenabled":
        case "autowiredEnabled": target.setAutowiredEnabled(property(camelContext, boolean.class, value)); return true;
        case "bedrockruntimeclient":
        case "bedrockRuntimeClient": getOrCreateConfiguration(target).setBedrockRuntimeClient(property(camelContext, software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient.class, value)); return true;
        case "configuration": target.setConfiguration(property(camelContext, org.apache.camel.component.aws2.bedrock.runtime.BedrockConfiguration.class, value)); return true;
        case "healthcheckconsumerenabled":
        case "healthCheckConsumerEnabled": target.setHealthCheckConsumerEnabled(property(camelContext, boolean.class, value)); return true;
        case "healthcheckproducerenabled":
        case "healthCheckProducerEnabled": target.setHealthCheckProducerEnabled(property(camelContext, boolean.class, value)); return true;
        case "lazystartproducer":
        case "lazyStartProducer": target.setLazyStartProducer(property(camelContext, boolean.class, value)); return true;
        case "modelid":
        case "modelId": getOrCreateConfiguration(target).setModelId(property(camelContext, java.lang.String.class, value)); return true;
        case "operation": getOrCreateConfiguration(target).setOperation(property(camelContext, org.apache.camel.component.aws2.bedrock.runtime.BedrockOperations.class, value)); return true;
        case "overrideendpoint":
        case "overrideEndpoint": getOrCreateConfiguration(target).setOverrideEndpoint(property(camelContext, boolean.class, value)); return true;
        case "pojorequest":
        case "pojoRequest": getOrCreateConfiguration(target).setPojoRequest(property(camelContext, boolean.class, value)); return true;
        case "profilecredentialsname":
        case "profileCredentialsName": getOrCreateConfiguration(target).setProfileCredentialsName(property(camelContext, java.lang.String.class, value)); return true;
        case "proxyhost":
        case "proxyHost": getOrCreateConfiguration(target).setProxyHost(property(camelContext, java.lang.String.class, value)); return true;
        case "proxyport":
        case "proxyPort": getOrCreateConfiguration(target).setProxyPort(property(camelContext, java.lang.Integer.class, value)); return true;
        case "proxyprotocol":
        case "proxyProtocol": getOrCreateConfiguration(target).setProxyProtocol(property(camelContext, software.amazon.awssdk.core.Protocol.class, value)); return true;
        case "region": getOrCreateConfiguration(target).setRegion(property(camelContext, java.lang.String.class, value)); return true;
        case "secretkey":
        case "secretKey": getOrCreateConfiguration(target).setSecretKey(property(camelContext, java.lang.String.class, value)); return true;
        case "sessiontoken":
        case "sessionToken": getOrCreateConfiguration(target).setSessionToken(property(camelContext, java.lang.String.class, value)); return true;
        case "trustallcertificates":
        case "trustAllCertificates": getOrCreateConfiguration(target).setTrustAllCertificates(property(camelContext, boolean.class, value)); return true;
        case "uriendpointoverride":
        case "uriEndpointOverride": getOrCreateConfiguration(target).setUriEndpointOverride(property(camelContext, java.lang.String.class, value)); return true;
        case "usedefaultcredentialsprovider":
        case "useDefaultCredentialsProvider": getOrCreateConfiguration(target).setUseDefaultCredentialsProvider(property(camelContext, boolean.class, value)); return true;
        case "useprofilecredentialsprovider":
        case "useProfileCredentialsProvider": getOrCreateConfiguration(target).setUseProfileCredentialsProvider(property(camelContext, boolean.class, value)); return true;
        case "usesessioncredentials":
        case "useSessionCredentials": getOrCreateConfiguration(target).setUseSessionCredentials(property(camelContext, boolean.class, value)); return true;
        default: return false;
        }
    }

    @Override
    public String[] getAutowiredNames() {
        return new String[]{"bedrockRuntimeClient"};
    }

    @Override
    public Class<?> getOptionType(String name, boolean ignoreCase) {
        switch (ignoreCase ? name.toLowerCase() : name) {
        case "accesskey":
        case "accessKey": return java.lang.String.class;
        case "autowiredenabled":
        case "autowiredEnabled": return boolean.class;
        case "bedrockruntimeclient":
        case "bedrockRuntimeClient": return software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient.class;
        case "configuration": return org.apache.camel.component.aws2.bedrock.runtime.BedrockConfiguration.class;
        case "healthcheckconsumerenabled":
        case "healthCheckConsumerEnabled": return boolean.class;
        case "healthcheckproducerenabled":
        case "healthCheckProducerEnabled": return boolean.class;
        case "lazystartproducer":
        case "lazyStartProducer": return boolean.class;
        case "modelid":
        case "modelId": return java.lang.String.class;
        case "operation": return org.apache.camel.component.aws2.bedrock.runtime.BedrockOperations.class;
        case "overrideendpoint":
        case "overrideEndpoint": return boolean.class;
        case "pojorequest":
        case "pojoRequest": return boolean.class;
        case "profilecredentialsname":
        case "profileCredentialsName": return java.lang.String.class;
        case "proxyhost":
        case "proxyHost": return java.lang.String.class;
        case "proxyport":
        case "proxyPort": return java.lang.Integer.class;
        case "proxyprotocol":
        case "proxyProtocol": return software.amazon.awssdk.core.Protocol.class;
        case "region": return java.lang.String.class;
        case "secretkey":
        case "secretKey": return java.lang.String.class;
        case "sessiontoken":
        case "sessionToken": return java.lang.String.class;
        case "trustallcertificates":
        case "trustAllCertificates": return boolean.class;
        case "uriendpointoverride":
        case "uriEndpointOverride": return java.lang.String.class;
        case "usedefaultcredentialsprovider":
        case "useDefaultCredentialsProvider": return boolean.class;
        case "useprofilecredentialsprovider":
        case "useProfileCredentialsProvider": return boolean.class;
        case "usesessioncredentials":
        case "useSessionCredentials": return boolean.class;
        default: return null;
        }
    }

    @Override
    public Object getOptionValue(Object obj, String name, boolean ignoreCase) {
        BedrockComponent target = (BedrockComponent) obj;
        switch (ignoreCase ? name.toLowerCase() : name) {
        case "accesskey":
        case "accessKey": return getOrCreateConfiguration(target).getAccessKey();
        case "autowiredenabled":
        case "autowiredEnabled": return target.isAutowiredEnabled();
        case "bedrockruntimeclient":
        case "bedrockRuntimeClient": return getOrCreateConfiguration(target).getBedrockRuntimeClient();
        case "configuration": return target.getConfiguration();
        case "healthcheckconsumerenabled":
        case "healthCheckConsumerEnabled": return target.isHealthCheckConsumerEnabled();
        case "healthcheckproducerenabled":
        case "healthCheckProducerEnabled": return target.isHealthCheckProducerEnabled();
        case "lazystartproducer":
        case "lazyStartProducer": return target.isLazyStartProducer();
        case "modelid":
        case "modelId": return getOrCreateConfiguration(target).getModelId();
        case "operation": return getOrCreateConfiguration(target).getOperation();
        case "overrideendpoint":
        case "overrideEndpoint": return getOrCreateConfiguration(target).isOverrideEndpoint();
        case "pojorequest":
        case "pojoRequest": return getOrCreateConfiguration(target).isPojoRequest();
        case "profilecredentialsname":
        case "profileCredentialsName": return getOrCreateConfiguration(target).getProfileCredentialsName();
        case "proxyhost":
        case "proxyHost": return getOrCreateConfiguration(target).getProxyHost();
        case "proxyport":
        case "proxyPort": return getOrCreateConfiguration(target).getProxyPort();
        case "proxyprotocol":
        case "proxyProtocol": return getOrCreateConfiguration(target).getProxyProtocol();
        case "region": return getOrCreateConfiguration(target).getRegion();
        case "secretkey":
        case "secretKey": return getOrCreateConfiguration(target).getSecretKey();
        case "sessiontoken":
        case "sessionToken": return getOrCreateConfiguration(target).getSessionToken();
        case "trustallcertificates":
        case "trustAllCertificates": return getOrCreateConfiguration(target).isTrustAllCertificates();
        case "uriendpointoverride":
        case "uriEndpointOverride": return getOrCreateConfiguration(target).getUriEndpointOverride();
        case "usedefaultcredentialsprovider":
        case "useDefaultCredentialsProvider": return getOrCreateConfiguration(target).isUseDefaultCredentialsProvider();
        case "useprofilecredentialsprovider":
        case "useProfileCredentialsProvider": return getOrCreateConfiguration(target).isUseProfileCredentialsProvider();
        case "usesessioncredentials":
        case "useSessionCredentials": return getOrCreateConfiguration(target).isUseSessionCredentials();
        default: return null;
        }
    }
}
