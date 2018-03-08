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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ComponentVerifier;
import org.apache.camel.Endpoint;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.VerifiableComponent;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
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

    private final String baseScheme;
    private final String componentScheme;
    private final String componentName;
    private final ConnectorModel model;
    private final Map<String, Object> options;
    private Processor beforeProducer;
    private Processor afterProducer;
    private Processor beforeConsumer;
    private Processor afterConsumer;

    protected DefaultConnectorComponent(String componentName, String className) {
        this(componentName, null, loadConnectorClass(className));
    }

    protected DefaultConnectorComponent(String componentName, String componentScheme, String className) {
        this(componentName, componentScheme, loadConnectorClass(className));
    }

    protected DefaultConnectorComponent(String componentName, Class<?> componentClass) {
        this(componentName, null, componentClass);
    }

    protected DefaultConnectorComponent(String componentName, String componentScheme, Class<?> componentClass) {
        this.model = new ConnectorModel(componentName, componentClass);
        this.baseScheme = this.model.getBaseScheme();
        this.componentName = componentName;
        this.componentScheme = componentScheme != null ? baseScheme + "-" + componentScheme : baseScheme + "-" + componentName + "-component";
        this.options = new HashMap<>();

        // add to catalog
        this.catalog.addComponent(componentName, componentClass.getName());

        // It may be a custom component so we need to register this in the camel catalog also
        if (!catalog.findComponentNames().contains(baseScheme)) {
            catalog.addComponent(baseScheme,  model.getBaseJavaType());
        }

        // Add an alias for the base component so there's no clash between connectors
        // if they set options targeting the component.
        if (!catalog.findComponentNames().contains(this.componentScheme)) {
            this.catalog.addComponent(this.componentScheme, this.model.getBaseJavaType(), catalog.componentJSonSchema(baseScheme));
        }

        registerExtension(this::getComponentVerifierExtension);
    }

    private static Class<?> loadConnectorClass(String className) {
        try {
            ClassLoader classLoader = DefaultConnectorComponent.class.getClassLoader();
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected <T> void doAddOption(Map<String, T> options, String name, T value) {
        log.trace("Adding option: {}={}", name, value);
        T val = options.put(name, value);
        if (val != null) {
            log.debug("Options {} overridden, old value was {}", name, val);
        }
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

        // create the uri of the base component
        String delegateUri = createEndpointUri(componentScheme, options);
        Endpoint delegate = getCamelContext().getEndpoint(delegateUri);

        if (log.isInfoEnabled()) {
            // the uris can have sensitive information so sanitize
            log.info("Connector resolved: {} -> {}", sanitizeUri(uri), sanitizeUri(delegateUri));
        }

        DefaultConnectorEndpoint answer;
        // are we scheduler based?
        if ("timer".equals(model.getScheduler())) {
            SchedulerTimerConnectorEndpoint endpoint = new SchedulerTimerConnectorEndpoint(uri, this, delegate, model.getInputDataType(), model.getOutputDataType());
            setProperties(endpoint, schedulerOptions);
            answer = endpoint;
        } else {
            answer = new DefaultConnectorEndpoint(uri, this, delegate, model.getInputDataType(), model.getOutputDataType());
        }

        answer.setBeforeProducer(getBeforeProducer());
        answer.setAfterProducer(getAfterProducer());
        answer.setBeforeConsumer(getBeforeConsumer());
        answer.setAfterConsumer(getAfterConsumer());

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

    @Override
    public String getComponentScheme() {
        return componentScheme;
    }

    @Override
    public Map<String, Object> getOptions() {
        return options;
    }

    @Override
    public void setOptions(Map<String, Object> options) {
        this.options.clear();
        this.options.putAll(options);
    }

    @Override
    public void addOption(String name, Object value) {
        doAddOption(this.options, name, value);
    }

    @Override
    public void addOptions(Map<String, Object> options) {
        options.forEach((name, value)->  doAddOption(this.options, name, value));
    }

    @Override
    public ComponentVerifier getVerifier() {
        return (scope, parameters) -> getExtension(ComponentVerifierExtension.class).orElseThrow(UnsupportedOperationException::new).verify(scope, parameters);
    }

    private ComponentVerifierExtension getComponentVerifierExtension() {
        try {
            // Create the component but no need to add it to the camel context
            // nor to start it.
            final Component component = createNewBaseComponent();
            final Optional<ComponentVerifierExtension> extension = component.getExtension(ComponentVerifierExtension.class);

            if (extension.isPresent()) {
                return (ComponentVerifierExtension.Scope scope, Map<String, Object> map) -> {
                    Map<String, Object> options;

                    try {
                        // A little nasty hack required as verifier uses Map<String, Object>
                        // to be compatible with all the methods in CamelContext whereas
                        // catalog deals with Map<String, String>
                        options = (Map) buildEndpointOptions(null, map);
                    } catch (URISyntaxException | NoTypeConversionAvailableException e) {
                        // If a failure is detected while reading the catalog, wrap it
                        // and stop the validation step.
                        return ResultBuilder.withStatusAndScope(ComponentVerifierExtension.Result.Status.OK, scope)
                            .error(ResultErrorBuilder.withException(e).build())
                            .build();
                    }

                    return extension.get().verify(scope, options);
                };
            } else {
                return (scope, map) -> {
                    return ResultBuilder.withStatusAndScope(ComponentVerifierExtension.Result.Status.UNSUPPORTED, scope)
                        .error(
                            ResultErrorBuilder.withCode(ComponentVerifierExtension.VerificationError.StandardCode.UNSUPPORTED)
                                .detail("camel_connector_name", getConnectorName())
                                .detail("camel_component_name", getComponentName())
                                .build())
                        .build();
                };
            }
        } catch (Exception e) {
            return (scope, map) -> {
                return ResultBuilder.withStatusAndScope(ComponentVerifierExtension.Result.Status.OK, scope)
                    .error(ResultErrorBuilder.withException(e).build())
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

        Component component = createNewBaseComponent();
        if (component != null) {
            log.info("Register component: {} (type: {}) with scheme: {}",
                this.componentName,
                component.getClass().getName(),
                this.componentScheme
            );

            //String delegateComponentScheme =
            getCamelContext().removeComponent(this.componentScheme);

            // ensure component is started and stopped when Camel shutdown
            getCamelContext().addService(component, true, true);
            getCamelContext().addComponent(this.componentScheme, component);
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

    /**
     * Create the endpoint instance which either happens with a new base component
     * which has been pre-configured for this connector or we fallback and use
     * the default component in the camel context
     */
    private Component createNewBaseComponent() throws Exception {
        final String baseClassName = model.getBaseJavaType();
        final CamelContext context = getCamelContext();

        Component base = null;

        if (baseClassName != null) {
            // create a new instance of this base component
            Class<?> type = Class.forName(baseClassName);
            Constructor ctr = getPublicDefaultConstructor(type);
            if (ctr != null) {
                // call default no-arg constructor
                base = (Component)ctr.newInstance();
                base.setCamelContext(context);

                // the connector may have default values for the component level also
                // and if so we need to prepare these values and set on this component
                // before we can start
                Map<String, Object> defaultOptions = model.getDefaultComponentOptions();

                if (!defaultOptions.isEmpty()) {
                    for (Map.Entry<String, Object> entry : defaultOptions.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        if (value != null) {
                            // also support {{ }} placeholders so resolve those first
                            value = getCamelContext().resolvePropertyPlaceholders(value.toString());

                            log.debug("Using component option: {}={}", key, value);
                            IntrospectionSupport.setProperty(context, base, key, value);
                        }
                    }
                }

                // configure component with extra options
                if (options != null && !options.isEmpty()) {
                    // Get the list of options from the connector catalog that
                    // are configured to target the endpoint
                    List<String> endpointOptions = model.getEndpointOptions();
                    Map<String, Object> connectorOptions = model.getConnectorOptions();

                    for (Map.Entry<String, Object> entry : options.entrySet()) {
                        // Only set options that are targeting the component
                        if (!endpointOptions.contains(entry.getKey()) && !connectorOptions.containsKey(entry.getKey())) {
                            log.debug("Using component option: {}={}", entry.getKey(), entry.getValue());
                            IntrospectionSupport.setProperty(context, base, entry.getKey(), entry.getValue());
                        }
                    }
                }
            }
        }

        return base;
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

    private Map<String, String> buildEndpointOptions(String remaining, Map<String, Object> parameters) throws URISyntaxException, NoTypeConversionAvailableException {
        Map<String, Object> defaultOptions = model.getDefaultEndpointOptions();

        // gather all options to use when building the delegate uri
        Map<String, String> options = new LinkedHashMap<>();

        // default options from connector json
        if (!defaultOptions.isEmpty()) {
            defaultOptions.forEach((key, value) -> {
                if (isValidConnectionOption(key, value)) {
                    String text = value.toString();
                    doAddOption(options, key, text);
                }
            });
        }

        // Extract options from options that are supposed to be set at the endpoint
        // level, those options can be overridden and extended using by the query
        // parameters.
        List<String> endpointOptions = model.getEndpointOptions();
        if (ObjectHelper.isNotEmpty(endpointOptions) && ObjectHelper.isNotEmpty(this.options)) {
            for (String endpointOption : endpointOptions) {
                Object value = this.options.get(endpointOption);
                if (value != null) {
                    doAddOption(
                        options,
                        endpointOption,
                        getCamelContext().getTypeConverter().mandatoryConvertTo(String.class, value));
                }
            }
        }

        // options from query parameters
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String value = null;
            if (entry.getValue() != null) {
                value = entry.getValue().toString();
            }
            if (isValidConnectionOption(key, value)) {
                doAddOption(options, key, value);
            }
        }

        // add extra options from remaining (context-path)
        if (remaining != null) {
            String targetUri = componentScheme + ":" + remaining;
            Map<String, String> extra = catalog.endpointProperties(targetUri);
            if (extra != null && !extra.isEmpty()) {
                extra.forEach((key, value) -> {
                    if (isValidConnectionOption(key, value)) {
                        doAddOption(options, key, value);
                    }
                });
            }
        }

        return options;
    }

    private boolean isValidConnectionOption(String key, Object value) {
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

