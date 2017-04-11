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
package org.apache.camel.component.connector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.ComponentVerifier;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.VerifiableComponent;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.verifier.ResultBuilder;
import org.apache.camel.impl.verifier.ResultErrorBuilder;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.URISupport.sanitizeUri;

/**
 * Base class for Camel Connector components.
 */
public abstract class DefaultConnectorComponent extends DefaultComponent implements ConnectorComponent, VerifiableComponent {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final CamelCatalog catalog = new DefaultCamelCatalog(false);

    private final String componentName;
    private final ConnectorModel model;
    private Map<String, Object> componentOptions;
    private Processor beforeProducer;
    private Processor afterProducer;
    private Processor beforeConsumer;
    private Processor afterConsumer;

    protected DefaultConnectorComponent(String componentName, String className) {
        this.componentName = componentName;
        this.model = new ConnectorModel(componentName, className);

        // add to catalog
        this.catalog.addComponent(componentName, className);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // if we extracted any scheduler query parameters we would need to rebuild the uri without them
        int before = parameters.size();
        Map<String, Object> schedulerOptions = extractSchedulerOptions(parameters);
        int after = parameters.size();
        if (schedulerOptions != null && before != after) {
            URI u = new URI(uri);
            u = URISupport.createRemainingURI(u, parameters);
            uri = u.toString();
        }
        // grab the regular query parameters
        Map<String, String> options = buildEndpointOptions(remaining, parameters);

        String scheme = model.getBaseScheme();

        // now create the endpoint instance which either happens with a new
        // base component which has been pre-configured for this connector
        // or we fallback and use the default component in the camel context
        createNewBaseComponent(scheme);

        // create the uri of the base component
        String delegateUri = createEndpointUri(scheme, options);
        Endpoint delegate = getCamelContext().getEndpoint(delegateUri);
        if (log.isInfoEnabled()) {
            // the uris can have sensitive information so sanitize
            log.info("Connector resolved: {} -> {}", sanitizeUri(uri), sanitizeUri(delegateUri));
        }

        Endpoint answer;
        // are we scheduler based?
        if ("timer".equals(model.getScheduler())) {
            SchedulerTimerConnectorEndpoint endpoint = new SchedulerTimerConnectorEndpoint(uri, this, delegate, model.getInputDataType(), model.getOutputDataType());
            setProperties(endpoint, schedulerOptions);
            answer = endpoint;
        } else {
            answer = new DefaultConnectorEndpoint(uri, this, delegate, model.getInputDataType(), model.getOutputDataType());
        }

        // clean-up parameters so that validation won't fail later on
        // in DefaultConnectorComponent.validateParameters()
        parameters.clear();

        return answer;
    }

    @Override
    public String createEndpointUri(String scheme, Map<String, String> options) throws URISyntaxException {
        log.trace("Creating endpoint uri with scheme: {}", scheme);
        return catalog.asEndpointUri(scheme, options, false);
    }

    @Override
    public void addConnectorOption(Map<String, String> options, String name, String value) {
        log.trace("Adding option: {}={}", name, value);
        options.put(name, value);
    }

    @Override
    public CamelCatalog getCamelCatalog() {
        return catalog;
    }

    @Override
    public String getCamelConnectorJSon() {
        return model.getConnectorJSon();
    }

    @Override
    public String getConnectorName() {
        return model.getConnectorName();
    }

    @Override
    public String getComponentName() {
        return componentName;
    }

    public Map<String, Object> getComponentOptions() {
        return componentOptions;
    }

    public void setComponentOptions(Map<String, Object> baseComponentOptions) {
        this.componentOptions = baseComponentOptions;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ComponentVerifier getVerifier() {
        final String scheme = model.getBaseScheme();
        // only get or create component but do NOT start it as component
        final Component component = getCamelContext().getComponent(scheme, true, false);

        if (component instanceof VerifiableComponent) {
            return (scope, map) -> {
                Map<String, Object> options;

                try {
                    // A little nasty hack required as verifier uses Map<String, Object>
                    // to be compatible with all the methods in CamelContext whereas
                    // catalog deals with Map<String, String>
                    options = (Map) buildEndpointOptions(null, map);
                } catch (URISyntaxException e) {
                    // If a failure is detected while reading the catalog, wrap it
                    // and stop the validation step.
                    return ResultBuilder.withStatusAndScope(ComponentVerifier.Result.Status.OK, scope)
                        .error(ResultErrorBuilder.withException(e).build())
                        .build();
                }

                return ((VerifiableComponent)component).getVerifier().verify(scope, options);
            };
        } else {
            return (scope, map) -> {
                return ResultBuilder.withStatusAndScope(ComponentVerifier.Result.Status.UNSUPPORTED, scope)
                    .error(
                        ResultErrorBuilder.withCode(ComponentVerifier.VerificationError.StandardCode.UNSUPPORTED)
                            .detail("camel_connector_name", getConnectorName())
                            .detail("camel_component_name", getComponentName())
                            .build())
                    .build();
            };
        }
    }

    // --------------------------------------------------------------

    @Override
    protected void doStart() throws Exception {
        // lets enforce that every connector must have an input and output data type

        if (model.getInputDataType() == null) {
            throw new IllegalArgumentException("Camel connector must have inputDataType defined in camel-connector.json file");
        }
        if (model.getOutputDataType() == null) {
            throw new IllegalArgumentException("Camel connector must have outputDataType defined in camel-connector.json file");
        }
        if (model.getBaseScheme() == null) {
            throw new IllegalArgumentException("Camel connector must have baseSchema defined in camel-connector.json file");
        }
        if (model.getBaseJavaType() == null) {
            throw new IllegalArgumentException("Camel connector must have baseJavaType defined in camel-connector.json file");
        }

        // it may be a custom component so we need to register this in the camel catalog also
        String scheme = model.getBaseScheme();
        if (!catalog.findComponentNames().contains(scheme)) {
            String javaType = model.getBaseJavaType();
            catalog.addComponent(scheme, javaType);
        }

        log.debug("Starting connector: {}", componentName);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("Stopping connector: {}", componentName);
        super.doStop();
    }

    @Override
    public Processor getBeforeProducer() {
        return beforeProducer;
    }

    @Override
    public void setBeforeProducer(Processor beforeProducer) {
        this.beforeProducer = beforeProducer;
    }

    @Override
    public Processor getAfterProducer() {
        return afterProducer;
    }

    @Override
    public void setAfterProducer(Processor afterProducer) {
        this.afterProducer = afterProducer;
    }

    @Override
    public Processor getBeforeConsumer() {
        return beforeConsumer;
    }

    @Override
    public void setBeforeConsumer(Processor beforeConsumer) {
        this.beforeConsumer = beforeConsumer;
    }

    @Override
    public Processor getAfterConsumer() {
        return afterConsumer;
    }

    @Override
    public void setAfterConsumer(Processor afterConsumer) {
        this.afterConsumer = afterConsumer;
    }

    // ***************************************
    // Helpers
    // ***************************************

    private Component createNewBaseComponent(String scheme) throws Exception {
        String baseClassName = model.getBaseJavaType();

        if (baseClassName != null) {
            // create a new instance of this base component
            Class<?> type = Class.forName(baseClassName);
            Constructor ctr = getPublicDefaultConstructor(type);
            if (ctr != null) {
                // call default no-arg constructor
                Object base = ctr.newInstance();

                // the connector may have default values for the component level also
                // and if so we need to prepare these values and set on this component before we can start
                Map<String, String> defaultOptions = model.getDefaultComponentOptions();

                if (!defaultOptions.isEmpty()) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    for (Map.Entry<String, String> entry : defaultOptions.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        if (value != null) {
                            // also support {{ }} placeholders so resolve those first
                            value = getCamelContext().resolvePropertyPlaceholders(value);
                            log.debug("Using component option: {}={}", key, value);
                            copy.put(key, value);
                        }
                    }
                    IntrospectionSupport.setProperties(getCamelContext(), getCamelContext().getTypeConverter(), base, copy);
                }

                // configure component with extra options
                if (componentOptions != null && !componentOptions.isEmpty()) {
                    Map<String, Object> copy = new LinkedHashMap<>(componentOptions);
                    IntrospectionSupport.setProperties(getCamelContext(), getCamelContext().getTypeConverter(), base, copy);
                }

                if (base instanceof Component) {
                    getCamelContext().removeComponent(scheme);
                    // ensure component is started and stopped when Camel shutdown
                    getCamelContext().addService(base, true, true);
                    getCamelContext().addComponent(scheme, (Component) base);

                    return (Component) base;
                }
            }
        }

        return null;
    }

    /**
     * Extracts the scheduler options from the parameters.
     * <p/>
     * These options start with <tt>scheduler</tt> in their key name, such as <tt>schedulerPeriod</tt>
     * which is removed from parameters, and transformed into keys without the <tt>scheduler</tt> prefix.
     *
     * @return the scheduler options, or <tt>null</tt> if scheduler not enabled
     */
    private Map<String, Object> extractSchedulerOptions(Map<String, Object> parameters) {
        if (model.getScheduler() != null) {
            // include default options first
            Map<String, Object> answer = new LinkedHashMap<>();
            model.getDefaultEndpointOptions().forEach((key, value) -> {
                String schedulerKey = asSchedulerKey(key);
                if (schedulerKey != null) {
                    answer.put(schedulerKey, value);
                }
            });

            // and then override with from parameters
            for (Iterator<Map.Entry<String, Object>> it = parameters.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, Object> entry = it.next();
                String schedulerKey = asSchedulerKey(entry.getKey());
                if (schedulerKey != null) {
                    Object value = entry.getValue();
                    answer.put(schedulerKey, value);
                    // and remove as it should not be part of regular parameters
                    it.remove();
                }
            }
            return answer;
        }

        return null;
    }

    private static String asSchedulerKey(String key) {
        if (key.startsWith("scheduler")) {
            String name = key.substring(9);
            // and lower case first char
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
            return name;
        } else {
            return null;
        }
    }

    private Map<String, String> buildEndpointOptions(String remaining, Map<String, Object> parameters) throws URISyntaxException {
        String scheme = model.getBaseScheme();
        Map<String, String> defaultOptions = model.getDefaultEndpointOptions();

        // gather all options to use when building the delegate uri
        Map<String, String> options = new LinkedHashMap<>();

        // default options from connector json
        if (!defaultOptions.isEmpty()) {
            defaultOptions.forEach((key, value) -> {
                if (isValidConnectionOption(key, value)) {
                    addConnectorOption(options, key, value);
                }
            });
        }

        // options from query parameters
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String value = null;
            if (entry.getValue() != null) {
                value = entry.getValue().toString();
            }
            if (isValidConnectionOption(key, value)) {
                addConnectorOption(options, key, value);
            }
        }

        // add extra options from remaining (context-path)
        if (remaining != null) {
            String targetUri = scheme + ":" + remaining;
            Map<String, String> extra = catalog.endpointProperties(targetUri);
            if (extra != null && !extra.isEmpty()) {
                extra.forEach((key, value) -> {
                    if (isValidConnectionOption(key, value)) {
                        addConnectorOption(options, key, value);
                    }
                });
            }
        }

        return options;
    }

    private boolean isValidConnectionOption(String key, String value) {
        // skip specific option if its a scheduler
        if (model.getScheduler() != null && asSchedulerKey(key) != null) {
            return false;
        }
        return true;
    }

    private static Constructor getPublicDefaultConstructor(Class<?> clazz) {
        for (Constructor ctr : clazz.getConstructors()) {
            if (Modifier.isPublic(ctr.getModifiers()) && ctr.getParameterCount() == 0) {
                return ctr;
            }
        }
        return null;
    }
}

