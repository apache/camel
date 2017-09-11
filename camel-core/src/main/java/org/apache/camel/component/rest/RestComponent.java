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
package org.apache.camel.component.rest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.ComponentVerifier;
import org.apache.camel.Endpoint;
import org.apache.camel.VerifiableComponent;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.model.rest.RestConstants;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * Rest component.
 */
@Metadata(label = "verifiers", enums = "parameters,connectivity")
public class RestComponent extends DefaultComponent implements VerifiableComponent {

    @Metadata(label = "common")
    private String componentName;
    @Metadata(label = "producer")
    private String apiDoc;
    @Metadata(label = "producer")
    private String host;

    public RestComponent() {
        registerExtension(RestComponentVerifierExtension::new);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String restConfigurationName = getAndRemoveParameter(parameters, "componentName", String.class, componentName);

        RestEndpoint answer = new RestEndpoint(uri, this);
        answer.setComponentName(restConfigurationName);
        answer.setApiDoc(apiDoc);

        RestConfiguration config = new RestConfiguration();
        mergeConfigurations(config, findGlobalRestConfiguration());
        mergeConfigurations(config, getCamelContext().getRestConfiguration(restConfigurationName, true));

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

        String method = ObjectHelper.before(remaining, ":");
        String s = ObjectHelper.after(remaining, ":");

        String path;
        String uriTemplate;
        if (s != null && s.contains(":")) {
            path = ObjectHelper.before(s, ":");
            uriTemplate = ObjectHelper.after(s, ":");
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
        if (answer.getComponentName() == null) {
            String name = config.getProducerComponent();
            if (name == null) {
                // fallback and use the consumer name
                name = config.getComponent();
            }
            answer.setComponentName(name);
        }
        // if no explicit producer api was given, then fallback and use default configured
        if (answer.getApiDoc() == null) {
            answer.setApiDoc(config.getProducerApiDoc());
        }

        return answer;
    }

    public String getComponentName() {
        return componentName;
    }

    /**
     * The Camel Rest component to use for the REST transport, such as restlet, spark-rest.
     * If no component has been explicit configured, then Camel will lookup if there is a Camel component
     * that integrates with the Rest DSL, or if a org.apache.camel.spi.RestConsumerFactory (consumer)
     * or org.apache.camel.spi.RestProducerFactory (producer) is registered in the registry.
     * If either one is found, then that is being used.
     */
    public void setComponentName(String componentName) {
        this.componentName = componentName;
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

        RestConfiguration conf = CamelContextHelper.lookup(context, RestConstants.DEFAULT_REST_CONFIGURATION_ID, RestConfiguration.class);
        if (conf == null) {
            conf = CamelContextHelper.findByType(getCamelContext(), RestConfiguration.class);
        }

        return conf;
    }

    private RestConfiguration mergeConfigurations(RestConfiguration conf, RestConfiguration from) throws Exception {
        if (conf == from) {
            return conf;
        }
        if (from != null) {
            Map<String, Object> map = IntrospectionSupport.getNonNullProperties(from);

            // Remove properties as they need to be manually managed
            Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Object> entry = it.next();
                if (entry.getValue() instanceof Map) {
                    it.remove();
                }
            }

            // Copy common options, will override those in conf
            IntrospectionSupport.setProperties(getCamelContext(), getCamelContext().getTypeConverter(), conf, map);

            // Merge properties
            mergeProperties(conf::getComponentProperties, from::getComponentProperties, conf::setComponentProperties);
            mergeProperties(conf::getEndpointProperties, from::getEndpointProperties, conf::setEndpointProperties);
            mergeProperties(conf::getConsumerProperties, from::getConsumerProperties, conf::setConsumerProperties);
            mergeProperties(conf::getDataFormatProperties, from::getDataFormatProperties, conf::setDataFormatProperties);
            mergeProperties(conf::getApiProperties, from::getApiProperties, conf::setApiProperties);
            mergeProperties(conf::getCorsHeaders, from::getCorsHeaders, conf::setCorsHeaders);
        }

        return conf;
    }

    private <T> void mergeProperties(Supplier<Map<String, T>> base, Supplier<Map<String, T>> addons, Consumer<Map<String, T>> consumer) {
        Map<String, T> baseMap = base.get();
        Map<String, T> addonsMap = addons.get();

        if (baseMap != null || addonsMap != null) {
            HashMap<String, T> result = new HashMap<>();
            if (baseMap != null) {
                result.putAll(baseMap);
            }
            if (addonsMap != null) {
                result.putAll(addonsMap);
            }

            consumer.accept(result);
        }
    }

    @Override
    public ComponentVerifier getVerifier() {
        return (scope, parameters) -> getExtension(ComponentVerifierExtension.class).orElseThrow(UnsupportedOperationException::new).verify(scope, parameters);
    }
}
