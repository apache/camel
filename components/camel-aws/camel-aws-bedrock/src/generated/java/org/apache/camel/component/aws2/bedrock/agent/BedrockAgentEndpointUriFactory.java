/* Generated by camel build tools - do NOT edit this file! */
package org.apache.camel.component.aws2.bedrock.agent;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.spi.EndpointUriFactory;

/**
 * Generated by camel build tools - do NOT edit this file!
 */
public class BedrockAgentEndpointUriFactory extends org.apache.camel.support.component.EndpointUriFactorySupport implements EndpointUriFactory {

    private static final String BASE = ":label";

    private static final Set<String> PROPERTY_NAMES;
    private static final Set<String> SECRET_PROPERTY_NAMES;
    private static final Set<String> MULTI_VALUE_PREFIXES;
    static {
        Set<String> props = new HashSet<>(42);
        props.add("accessKey");
        props.add("backoffErrorThreshold");
        props.add("backoffIdleThreshold");
        props.add("backoffMultiplier");
        props.add("bedrockAgentClient");
        props.add("bridgeErrorHandler");
        props.add("dataSourceId");
        props.add("delay");
        props.add("exceptionHandler");
        props.add("exchangePattern");
        props.add("greedy");
        props.add("ingestionJobId");
        props.add("initialDelay");
        props.add("knowledgeBaseId");
        props.add("label");
        props.add("lazyStartProducer");
        props.add("modelId");
        props.add("operation");
        props.add("overrideEndpoint");
        props.add("pojoRequest");
        props.add("pollStrategy");
        props.add("profileCredentialsName");
        props.add("proxyHost");
        props.add("proxyPort");
        props.add("proxyProtocol");
        props.add("region");
        props.add("repeatCount");
        props.add("runLoggingLevel");
        props.add("scheduledExecutorService");
        props.add("scheduler");
        props.add("schedulerProperties");
        props.add("secretKey");
        props.add("sendEmptyMessageWhenIdle");
        props.add("sessionToken");
        props.add("startScheduler");
        props.add("timeUnit");
        props.add("trustAllCertificates");
        props.add("uriEndpointOverride");
        props.add("useDefaultCredentialsProvider");
        props.add("useFixedDelay");
        props.add("useProfileCredentialsProvider");
        props.add("useSessionCredentials");
        PROPERTY_NAMES = Collections.unmodifiableSet(props);
        Set<String> secretProps = new HashSet<>(3);
        secretProps.add("accessKey");
        secretProps.add("secretKey");
        secretProps.add("sessionToken");
        SECRET_PROPERTY_NAMES = Collections.unmodifiableSet(secretProps);
        Set<String> prefixes = new HashSet<>(1);
        prefixes.add("scheduler.");
        MULTI_VALUE_PREFIXES = Collections.unmodifiableSet(prefixes);
    }

    @Override
    public boolean isEnabled(String scheme) {
        return "aws-bedrock-agent".equals(scheme);
    }

    @Override
    public String buildUri(String scheme, Map<String, Object> properties, boolean encode) throws URISyntaxException {
        String syntax = scheme + BASE;
        String uri = syntax;

        Map<String, Object> copy = new HashMap<>(properties);

        uri = buildPathParameter(syntax, uri, "label", null, true, copy);
        uri = buildQueryParameters(uri, copy, encode);
        return uri;
    }

    @Override
    public Set<String> propertyNames() {
        return PROPERTY_NAMES;
    }

    @Override
    public Set<String> secretPropertyNames() {
        return SECRET_PROPERTY_NAMES;
    }

    @Override
    public Set<String> multiValuePrefixes() {
        return MULTI_VALUE_PREFIXES;
    }

    @Override
    public boolean isLenientProperties() {
        return false;
    }
}
