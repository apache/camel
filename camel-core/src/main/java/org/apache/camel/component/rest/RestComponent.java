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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestEndpointConfigurer;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * Rest component.
 */
public class RestComponent extends UriEndpointComponent {

    private static final Set<String> HTTP_METHODS = Collections.unmodifiableSet(new TreeSet<String>(String.CASE_INSENSITIVE_ORDER) {
        {
            addAll(Arrays.asList("get", "post", "put", "delete", "patch", "head", "trace", "connect", "options"));
        }
    });

    @Metadata(label = "common")
    private String componentName;
    @Metadata(label = "producer")
    private String apiDoc;
    @Metadata(label = "producer")
    private String host;

    public RestComponent() {
        super(RestEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final String methodOrComponent = StringHelper.before(remaining, ":");

        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException("Invalid syntax. Must be rest:method:path[:uriTemplate] or rest:<component>[:componentSpecific], "
                + "where uriTemplate is optional and componentSpecific is optional and component specific");
        }

        final RestEndpoint answer = new RestEndpoint(uri, this);
        answer.setComponentName(componentName);

        final CamelContext context = getCamelContext();
        final RestConfiguration restConfiguration = context.getRestConfiguration();

        // if no explicit component name was given, then fallback and use default configured component name
        if (answer.getComponentName() == null && restConfiguration != null) {
            String name = restConfiguration.getProducerComponent();
            if (name == null) {
                // fallback and use the consumer name
                name = restConfiguration.getComponent();
            }
            answer.setComponentName(name);
        }

        // if no explicit producer api was given, then fallback and use default configured
        if (apiDoc == null && restConfiguration != null) {
            answer.setApiDoc(restConfiguration.getProducerApiDoc());
        }

        setProperties(answer, parameters);
        answer.setParameters(parameters);

        // if the URI is in the format `rest:HTTP method:...`
        // else try to lookup component provided SPI to configure the endpoint
        if (HTTP_METHODS.contains(methodOrComponent)) {
            configureClassicEndpoint(answer, uri, remaining, parameters);
        } else {
            configureFromSpi(answer, uri, remaining, parameters);
        }


        return answer;
    }

    /**
     * Creates the <em>classic</em> {@link Endpoint}, configured from the endpoint URI and parameters.
     *
     * @param answer
     *            {@link RestEndpoint} to configure
     * @param uri
     *            the full URI of the endpoint
     * @param remaining
     *            the remaining part of the URI without the query parameters or component prefix
     * @param parameters
     *            the optional parameters passed in
     * @return
     * @throws Exception
     */
    protected void configureClassicEndpoint(final RestEndpoint answer, final String uri, final String remaining, final Map<String, Object> parameters) throws Exception {
        // if no explicit host was given, then fallback and use default configured host
        String h = Optional.ofNullable(answer.getHost()).orElse(host);
        if (h == null && getCamelContext().getRestConfiguration() != null) {
            h = getCamelContext().getRestConfiguration().getHost();
            int port = getCamelContext().getRestConfiguration().getPort();
            // is there a custom port number
            if (port > 0 && port != 80 && port != 443) {
                h += ":" + port;
            }
        }
        // host must start with http:// or https://
        if (h != null && !(h.startsWith("http://") || h.startsWith("https://"))) {
            h = "http://" + h;
        }

        if (ObjectHelper.isNotEmpty(h)) {
            answer.setHost(h);
        }

        String query = StringHelper.after(uri, "?");
        if (query != null) {
            answer.setQueryParameters(query);
        }

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
    }

    protected void configureFromSpi(final RestEndpoint answer, final String uri, final String remaining,
            final Map<String, Object> parameters) {

        final CamelContext context = getCamelContext();

        final RestEndpointConfigurer configurer;

        final String component = StringHelper.before(remaining, ":");
        try {
            final FactoryFinder finder = context.getFactoryFinder(RestEndpoint.RESOURCE_PATH);
            final Class<?> configurerClass = finder.findClass(component, "configurer.");

            final Injector injector = context.getInjector();
            final Object configurerInstance = injector.newInstance(configurerClass);
            if (configurerInstance instanceof RestEndpointConfigurer) {
                configurer = (RestEndpointConfigurer) configurerInstance;
            } else {
                throw new ClassCastException("The specified configurer: `" + configurerClass.getName()
                    + "` does not implement `" + RestEndpointConfigurer.class.getName()
                    + "` interface. Check that it does and check your classloading strategy.");
            }
        } catch (ClassNotFoundException | IOException e) {
            throw new IllegalStateException(
                    "Cannot find `" + component + "` on classpath to configure endpoint with URI: `" + uri
                        + "`. Is there a " + component + " on the classpath? Does it contain `"
                        + RestEndpoint.RESOURCE_PATH + component + "` with `configurer.class` property?", e);
        }

        final RestConfiguration restConfiguration = context.getRestConfiguration();
        if (restConfiguration != null) {
            restConfiguration.applyTo(answer);
        }

        configurer.configureEndpoint(context, answer, uri, remaining, parameters);
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

}
