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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.spi.*;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.HeaderFilterStrategyComponent;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

/**
 * Rest component.
 */
@org.apache.camel.spi.annotations.Component("rest")
@Metadata(label = "verifiers", enums = "parameters,connectivity")
public class RestComponent extends HeaderFilterStrategyComponent {

    public static final String DEFAULT_REST_CONFIGURATION_ID = "rest-configuration";

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

        RestConfiguration config = CamelContextHelper.getRestConfiguration(getCamelContext(), cname, pname);

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

        // custom header filter strategy
        if (getHeaderFilterStrategy() != null) {
            parameters.put("headerFilterStrategy", getHeaderFilterStrategy());
        }

        setProperties(answer, parameters);
        if (!parameters.isEmpty()) {
            // use only what remains and at this point parameters that have been used have been removed
            // without overwriting any query parameters set via queryParameters endpoint option
            final Map<String, Object> queryParameters = new LinkedHashMap<>(parameters);

            // filter out known options from the producer, as they should not be in the query parameters
            EndpointUriFactory factory = getEndpointUriFactory(pname);
            if (factory != null) {
                for (String key : parameters.keySet()) {
                    if (factory.propertyNames().contains(key)) {
                        queryParameters.remove(key);
                    }
                }
            }

            final Map<String, Object> existingQueryParameters = URISupport.parseQuery(answer.getQueryParameters());
            queryParameters.putAll(existingQueryParameters);

            final String remainingParameters = URISupport.createQueryString(queryParameters);
            if (ObjectHelper.isNotEmpty(remainingParameters)) {
                answer.setQueryParameters(remainingParameters);
            }
        }

        answer.setParameters(parameters);

        if (!remaining.contains(":")) {
            throw new IllegalArgumentException(
                    "Invalid syntax. Must be rest:method:path[:uriTemplate] where uriTemplate is optional");
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
     * The Camel Rest component to use for the consumer REST transport, such as jetty, servlet, undertow. If no
     * component has been explicitly configured, then Camel will lookup if there is a Camel component that integrates
     * with the Rest DSL, or if a org.apache.camel.spi.RestConsumerFactory is registered in the registry. If either one
     * is found, then that is being used.
     */
    public void setConsumerComponentName(String consumerComponentName) {
        this.consumerComponentName = consumerComponentName;
    }

    public String getProducerComponentName() {
        return producerComponentName;
    }

    /**
     * The Camel Rest component to use for the producer REST transport, such as http, undertow. If no component has been
     * explicitly configured, then Camel will lookup if there is a Camel component that integrates with the Rest DSL, or
     * if a org.apache.camel.spi.RestProducerFactory is registered in the registry. If either one is found, then that is
     * being used.
     */
    public void setProducerComponentName(String producerComponentName) {
        this.producerComponentName = producerComponentName;
    }

    public String getApiDoc() {
        return apiDoc;
    }

    /**
     * The swagger api doc resource to use. The resource is loaded from classpath by default and must be in JSON format.
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

    private EndpointUriFactory getEndpointUriFactory(String name) {
        if (name != null) {
            UriFactoryResolver resolver
                    = getCamelContext().getCamelContextExtension().getContextPlugin(UriFactoryResolver.class);
            if (resolver != null) {
                return resolver.resolveFactory(name, getCamelContext());
            }
        }
        return null;
    }

    public ComponentVerifierExtension getVerifier() {
        return (scope, parameters) -> getExtension(ComponentVerifierExtension.class)
                .orElseThrow(UnsupportedOperationException::new).verify(scope, parameters);
    }
}
