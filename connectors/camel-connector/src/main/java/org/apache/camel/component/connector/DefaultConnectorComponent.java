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
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Component;
import org.apache.camel.ComponentVerifier;
import org.apache.camel.Endpoint;
import org.apache.camel.VerifiableComponent;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.verifier.ResultBuilder;
import org.apache.camel.impl.verifier.ResultErrorBuilder;
import org.apache.camel.util.IntrospectionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for Camel Connector components.
 */
public abstract class DefaultConnectorComponent extends DefaultComponent implements ConnectorComponent, VerifiableComponent {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final CamelCatalog catalog = new DefaultCamelCatalog(false);

    private final String componentName;
    private final ConnectorModel model;
    private Map<String, Object> componentOptions;

    protected DefaultConnectorComponent(String componentName, String className) {
        this.componentName = componentName;
        this.model = new ConnectorModel(componentName, className);

        // add to catalog
        this.catalog.addComponent(componentName, className);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Map<String, String> options = buildEnpointOptions(remaining, parameters);

        // clean-up parameters so that validation won't fail later on
        // in DefaultConnectorComponent.validateParameters()
        parameters.clear();
        
        String scheme = model.getBaseScheme();

        // if we have component options then we need to create a new instance of the component
        // which we then configure before it create the endpoint
        if (componentOptions != null && !componentOptions.isEmpty()) {
            String baseClassName = model.getBaseJavaType();
            if (baseClassName != null) {
                Class<?> type = Class.forName(baseClassName);
                Constructor ctr = getPublicDefaultConstructor(type);
                if (ctr != null) {
                    // call default no-arg constructor
                    Object base = ctr.newInstance();
                    // configure component with extra options
                    Map<String, Object> copy = new HashMap<>(componentOptions);
                    IntrospectionSupport.setProperties(getCamelContext(), getCamelContext().getTypeConverter(), base, copy);

                    if (base instanceof Component) {
                        Component old = getCamelContext().removeComponent(scheme);
                        // ensure component is started and stopped when Camel shutdown
                        getCamelContext().addService(base, true, true);
                        getCamelContext().addComponent(scheme, (Component) base);
                    }
                }
            }
        }

        String delegateUri = createEndpointUri(scheme, options);

        log.debug("Connector resolved: {} -> {}", uri, delegateUri);
        Endpoint delegate = getCamelContext().getEndpoint(delegateUri);
        return new DefaultConnectorEndpoint(uri, this, delegate, model.getInputDataType(), model.getOutputDataType());
    }

    private static Constructor getPublicDefaultConstructor(Class<?> clazz) {
        for (Constructor ctr : clazz.getConstructors()) {
            if (Modifier.isPublic(ctr.getModifiers()) && ctr.getParameterCount() == 0) {
                return ctr;
            }
        }
        return null;
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
        final Component component = getCamelContext().getComponent(scheme);

        if (component instanceof VerifiableComponent) {
            return (scope, map) -> {
                Map<String, Object> options;

                try {
                    // A little nasty hack required as verifier uses Map<String, Object>
                    // to be compatible with all the methods in CamelContext whereas
                    // catalog deals with Map<String, String>
                    options = (Map)buildEnpointOptions(null, map);
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
                        ResultErrorBuilder.withCode("unsupported")
                            .attribute("camel.connector.name", getConnectorName())
                            .attribute("camel.component.name", getComponentName())
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

        // it may be a custom component so we need to register this in the camel catalog also
        String scheme = model.getBaseScheme();
        if (!catalog.findComponentNames().contains(scheme)) {
            String javaType = model.getBaseJavaType();
            catalog.addComponent(scheme, javaType);
        }

        // the connector may have default values for the component level also
        // and if so we need to prepare these values and set on this component before we can start
        Map<String, String> defaultOptions = model.getDefaultComponentOptions();

        if (!defaultOptions.isEmpty()) {
            Map<String, Object> parameters = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : defaultOptions.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (value != null) {
                    // also support {{ }} placeholders so resolve those first
                    value = getCamelContext().resolvePropertyPlaceholders(value);
                    log.debug("Using component option: {}={}", key, value);
                    parameters.put(key, value);
                }
            }
            IntrospectionSupport.setProperties(getCamelContext(), getCamelContext().getTypeConverter(), this, parameters);
        }

        log.debug("Starting connector: {}", componentName);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("Stopping connector: {}", componentName);
        super.doStop();
    }

    // ***************************************
    // Helpers
    // ***************************************

    private Map<String, String> buildEnpointOptions(String remaining, Map<String, Object> parameters) throws URISyntaxException {
        String scheme = model.getBaseScheme();
        Map<String, String> defaultOptions = model.getDefaultEndpointOptions();

        // gather all options to use when building the delegate uri
        Map<String, String> options = new LinkedHashMap<>();

        // default options from connector json
        if (!defaultOptions.isEmpty()) {
            defaultOptions.forEach((k, v) -> addConnectorOption(options, k, v));
        }
        // options from query parameters
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String value = null;
            if (entry.getValue() != null) {
                value = entry.getValue().toString();
            }
            addConnectorOption(options, key, value);
        }

        // add extra options from remaining (context-path)
        if (remaining != null) {
            String targetUri = scheme + ":" + remaining;
            Map<String, String> extra = catalog.endpointProperties(targetUri);
            if (extra != null && !extra.isEmpty()) {
                extra.forEach((k, v) -> addConnectorOption(options, k, v));
            }
        }

        return options;
    }
}

