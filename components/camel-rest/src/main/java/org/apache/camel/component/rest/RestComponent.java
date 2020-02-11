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
package org.apache.camel.component.rest;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConfiguration.RestBindingMode;
import org.apache.camel.spi.RestConfiguration.RestHostNameResolver;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

/**
 * Rest component.
 */
@org.apache.camel.spi.annotations.Component("rest")
@Metadata(label = "verifiers", enums = "parameters,connectivity")
public class RestComponent extends DefaultComponent {

    public static final String DEFAULT_REST_CONFIGURATION_ID = "rest-configuration";

    @Deprecated
    @Metadata(label = "producer")
    private String componentName;
    @Metadata(label = "consumer")
    private String consumerComponentName;
    @Metadata(label = "producer")
    private String producerComponentName;
    @Metadata(label = "producer")
    private String apiDoc;
    @Metadata(label = "producer")
    private String host;

    public RestComponent() {
        registerExtension(RestComponentVerifierExtension::new);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String cname = getAndRemoveParameter(parameters, "consumerComponentName", String.class, consumerComponentName);
        String pname = getAndRemoveParameter(parameters, "producerComponentName", String.class, producerComponentName);

        RestEndpoint answer = new RestEndpoint(uri, this);
        answer.setConsumerComponentName(cname);
        answer.setProducerComponentName(pname);
        answer.setApiDoc(apiDoc);

        RestConfiguration config = new RestConfiguration();
        mergeConfigurations(getCamelContext(), config, findGlobalRestConfiguration());
        mergeConfigurations(getCamelContext(), config, getCamelContext().getRestConfiguration(cname, false));
        mergeConfigurations(getCamelContext(), config, getCamelContext().getRestConfiguration(pname, false));

        // if no explicit host was given, then fallback and use default configured host
        String h = getAndRemoveOrResolveReferenceParameter(parameters, "host", String.class, host);
        if (h == null) {
            h = config.getHost();
            int port = config.getPort();
            // is there a custom port number
            if (port > 0 && port != 80 && port != 443) {
                h += ":" + port;
            }
        }
        // host must start with http:// or https://
        if (h != null && !(h.startsWith("http://") || h.startsWith("https://"))) {
            h = "http://" + h;
        }
        answer.setHost(h);

        setProperties(answer, parameters);
        if (!parameters.isEmpty()) {
            // use only what remains and at this point parameters that have been used have been removed
            // without overwriting any query parameters set via queryParameters endpoint option
            final Map<String, Object> queryParameters = new LinkedHashMap<>(parameters);
            final Map<String, Object> existingQueryParameters = URISupport.parseQuery(answer.getQueryParameters());
            queryParameters.putAll(existingQueryParameters);

            final String remainingParameters = URISupport.createQueryString(queryParameters);
            answer.setQueryParameters(remainingParameters);
        }

        answer.setParameters(parameters);

        if (!remaining.contains(":")) {
            throw new IllegalArgumentException("Invalid syntax. Must be rest:method:path[:uriTemplate] where uriTemplate is optional");
        }

        String method = StringHelper.before(remaining, ":");
        String s = StringHelper.after(remaining, ":");

        String path;
        String uriTemplate;
        if (s != null && s.contains(":")) {
            path = StringHelper.before(s, ":");
            uriTemplate = StringHelper.after(s, ":");
        } else {
            path = s;
            uriTemplate = null;
        }

        // remove trailing slashes
        path = FileUtil.stripTrailingSeparator(path);
        uriTemplate = FileUtil.stripTrailingSeparator(uriTemplate);

        answer.setMethod(method);
        answer.setPath(path);
        answer.setUriTemplate(uriTemplate);

        // if no explicit component name was given, then fallback and use default configured component name
        if (answer.getProducerComponentName() == null) {
            String name = config.getProducerComponent();
            answer.setProducerComponentName(name);
        }
        if (answer.getConsumerComponentName() == null) {
            String name = config.getComponent();
            answer.setConsumerComponentName(name);
        }
        // if no explicit producer api was given, then fallback and use default configured
        if (answer.getApiDoc() == null) {
            answer.setApiDoc(config.getProducerApiDoc());
        }

        return answer;
    }

    public String getConsumerComponentName() {
        return consumerComponentName;
    }

    /**
     * The Camel Rest component to use for (consumer) the REST transport, such as jetty, servlet, undertow.
     * If no component has been explicit configured, then Camel will lookup if there is a Camel component
     * that integrates with the Rest DSL, or if a org.apache.camel.spi.RestConsumerFactory is registered in the registry.
     * If either one is found, then that is being used.
     */
    public void setConsumerComponentName(String consumerComponentName) {
        this.consumerComponentName = consumerComponentName;
    }

    public String getProducerComponentName() {
        return producerComponentName;
    }

    /**
     * The Camel Rest component to use for (producer) the REST transport, such as http, undertow.
     * If no component has been explicit configured, then Camel will lookup if there is a Camel component
     * that integrates with the Rest DSL, or if a org.apache.camel.spi.RestProducerFactory is registered in the registry.
     * If either one is found, then that is being used.
     */
    public void setProducerComponentName(String producerComponentName) {
        this.producerComponentName = producerComponentName;
    }

    @Deprecated
    public String getComponentName() {
        return producerComponentName;
    }

    /**
     * The Camel Rest component to use for (producer) the REST transport, such as http, undertow.
     * If no component has been explicit configured, then Camel will lookup if there is a Camel component
     * that integrates with the Rest DSL, or if a org.apache.camel.spi.RestProducerFactory is registered in the registry.
     * If either one is found, then that is being used.
     *
     * @deprecated use producerComponentName instead
     */
    @Deprecated
    public void setComponentName(String componentName) {
        this.producerComponentName = componentName;
    }

    public String getApiDoc() {
        return apiDoc;
    }

    /**
     * The swagger api doc resource to use.
     * The resource is loaded from classpath by default and must be in JSon format.
     */
    public void setApiDoc(String apiDoc) {
        this.apiDoc = apiDoc;
    }

    public String getHost() {
        return host;
    }

    /**
     * Host and port of HTTP service to use (override host in swagger schema)
     */
    public void setHost(String host) {
        this.host = host;
    }

    // ****************************************
    // Helpers
    // ****************************************

    private RestConfiguration findGlobalRestConfiguration() {
        CamelContext context = getCamelContext();

        RestConfiguration conf = CamelContextHelper.lookup(context, DEFAULT_REST_CONFIGURATION_ID, RestConfiguration.class);
        if (conf == null) {
            conf = CamelContextHelper.findByType(getCamelContext(), RestConfiguration.class);
        }

        return conf;
    }

    private RestConfiguration mergeConfigurations(CamelContext camelContext, RestConfiguration conf, RestConfiguration from) throws Exception {
        if (conf == from) {
            return conf;
        }
        if (from != null) {
            // Merge properties
            conf.setComponent(or(conf.getComponent(), from.getComponent()));
            conf.setApiComponent(or(conf.getApiComponent(), from.getApiComponent()));
            conf.setProducerComponent(or(conf.getProducerComponent(), from.getProducerComponent()));
            conf.setProducerApiDoc(or(conf.getProducerApiDoc(), from.getProducerApiDoc()));
            conf.setScheme(or(conf.getScheme(), from.getScheme()));
            conf.setHost(or(conf.getHost(), from.getHost()));
            conf.setUseXForwardHeaders(or(conf.isUseXForwardHeaders(), from.isUseXForwardHeaders()));
            conf.setApiHost(or(conf.getApiHost(), from.getApiHost()));
            conf.setPort(or(conf.getPort(), from.getPort()));
            conf.setContextPath(or(conf.getContextPath(), from.getContextPath()));
            conf.setApiContextPath(or(conf.getApiContextPath(), from.getApiContextPath()));
            conf.setApiContextRouteId(or(conf.getApiContextRouteId(), from.getApiContextRouteId()));
            conf.setApiContextIdPattern(or(conf.getApiContextIdPattern(), from.getApiContextIdPattern()));
            conf.setApiContextListing(or(conf.isApiContextListing(), from.isApiContextListing()));
            conf.setApiVendorExtension(or(conf.isApiVendorExtension(), from.isApiVendorExtension()));
            conf.setHostNameResolver(or(conf.getHostNameResolver(), from.getHostNameResolver(), RestHostNameResolver.allLocalIp));
            conf.setBindingMode(or(conf.getBindingMode(), from.getBindingMode(), RestBindingMode.off));
            conf.setSkipBindingOnErrorCode(or(conf.isSkipBindingOnErrorCode(), from.isSkipBindingOnErrorCode()));
            conf.setClientRequestValidation(or(conf.isClientRequestValidation(), from.isClientRequestValidation()));
            conf.setEnableCORS(or(conf.isEnableCORS(), from.isEnableCORS()));
            conf.setJsonDataFormat(or(conf.getJsonDataFormat(), from.getJsonDataFormat()));
            conf.setXmlDataFormat(or(conf.getXmlDataFormat(), from.getXmlDataFormat()));
            conf.setComponentProperties(mergeProperties(conf.getComponentProperties(), from.getComponentProperties()));
            conf.setEndpointProperties(mergeProperties(conf.getEndpointProperties(), from.getEndpointProperties()));
            conf.setConsumerProperties(mergeProperties(conf.getConsumerProperties(), from.getConsumerProperties()));
            conf.setDataFormatProperties(mergeProperties(conf.getDataFormatProperties(), from.getDataFormatProperties()));
            conf.setApiProperties(mergeProperties(conf.getApiProperties(), from.getApiProperties()));
            conf.setCorsHeaders(mergeProperties(conf.getCorsHeaders(), from.getCorsHeaders()));
        }

        return conf;
    }

    private <T> T or(T t1, T t2) {
        return t2 != null ? t2 : t1;
    }

    private <T> T or(T t1, T t2, T def) {
        return t2 != null && t2 != def ? t2 : t1;
    }

    private <T> Map<String, T> mergeProperties(Map<String, T> base, Map<String, T> addons) {
        if (base != null || addons != null) {
            Map<String, T> result = new HashMap<>();
            if (base != null) {
                result.putAll(base);
            }
            if (addons != null) {
                result.putAll(addons);
            }
            return result;
        }
        return base;
    }

    public ComponentVerifierExtension getVerifier() {
        return (scope, parameters) -> getExtension(ComponentVerifierExtension.class).orElseThrow(UnsupportedOperationException::new).verify(scope, parameters);
    }
}
