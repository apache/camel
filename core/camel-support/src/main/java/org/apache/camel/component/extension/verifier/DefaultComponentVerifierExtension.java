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
package org.apache.camel.component.extension.verifier;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Component;
import org.apache.camel.ComponentAware;
import org.apache.camel.catalog.EndpointValidationResult;
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.PropertiesHelper;

import static org.apache.camel.util.StreamUtils.stream;

public class DefaultComponentVerifierExtension implements ComponentVerifierExtension, CamelContextAware, ComponentAware {
    private final String defaultScheme;
    private Component component;
    private CamelContext camelContext;

    protected DefaultComponentVerifierExtension(String defaultScheme) {
        this(defaultScheme, null, null);
    }

    protected DefaultComponentVerifierExtension(String defaultScheme, CamelContext camelContext) {
        this(defaultScheme, camelContext, null);
    }

    protected DefaultComponentVerifierExtension(String defaultScheme, CamelContext camelContext, Component component) {
        this.defaultScheme = defaultScheme;
        this.camelContext = camelContext;
        this.component = component;
    }

    // *************************************
    //
    // *************************************

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public Component getComponent() {
        return component;
    }

    @Override
    public void setComponent(Component component) {
        this.component = component;
    }

    @Override
    public Result verify(Scope scope, Map<String, Object> parameters) {
        // Camel context is mandatory
        if (this.camelContext == null) {
            return ResultBuilder.withStatusAndScope(Result.Status.ERROR, scope)
                    .error(ResultErrorBuilder
                            .withCodeAndDescription(VerificationError.StandardCode.INTERNAL, "Missing camel-context").build())
                    .build();
        }

        if (scope == Scope.PARAMETERS) {
            return verifyParameters(parameters);
        }
        if (scope == Scope.CONNECTIVITY) {
            return verifyConnectivity(parameters);
        }

        return ResultBuilder.unsupportedScope(scope).build();
    }

    protected Result verifyConnectivity(Map<String, Object> parameters) {
        return ResultBuilder.withStatusAndScope(Result.Status.UNSUPPORTED, Scope.CONNECTIVITY).build();
    }

    protected Result verifyParameters(Map<String, Object> parameters) {
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS);

        // Validate against catalog
        verifyParametersAgainstCatalog(builder, parameters);

        return builder.build();
    }

    // *************************************
    // Helpers :: Parameters validation
    // *************************************

    protected void verifyParametersAgainstCatalog(ResultBuilder builder, Map<String, Object> parameters) {
        verifyParametersAgainstCatalog(builder, parameters, new CatalogVerifierCustomizer());
    }

    protected void verifyParametersAgainstCatalog(
            ResultBuilder builder, Map<String, Object> parameters, CatalogVerifierCustomizer customizer) {
        String scheme = defaultScheme;
        if (parameters.containsKey("scheme")) {
            scheme = parameters.get("scheme").toString();
        }

        // Grab the runtime catalog to check parameters
        RuntimeCamelCatalog catalog = PluginHelper.getRuntimeCamelCatalog(camelContext);

        // Convert from Map<String, Object> to  Map<String, String> as required
        // by the Camel Catalog
        EndpointValidationResult result = catalog.validateProperties(
                scheme,
                parameters.entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey,
                                        e -> camelContext.getTypeConverter().convertTo(String.class, e.getValue()))));

        if (!result.isSuccess()) {
            if (customizer.isIncludeUnknown()) {
                stream(result.getUnknown())
                        .map(option -> ResultErrorBuilder.withUnknownOption(option).build())
                        .forEach(builder::error);
            }
            if (customizer.isIncludeRequired()) {
                stream(result.getRequired())
                        .map(option -> ResultErrorBuilder.withMissingOption(option).build())
                        .forEach(builder::error);
            }
            if (customizer.isIncludeInvalidBoolean()) {
                stream(result.getInvalidBoolean())
                        .map(entry -> ResultErrorBuilder.withIllegalOption(entry.getKey(), entry.getValue()).build())
                        .forEach(builder::error);
            }
            if (customizer.isIncludeInvalidInteger()) {
                stream(result.getInvalidInteger())
                        .map(entry -> ResultErrorBuilder.withIllegalOption(entry.getKey(), entry.getValue()).build())
                        .forEach(builder::error);
            }
            if (customizer.isIncludeInvalidNumber()) {
                stream(result.getInvalidNumber())
                        .map(entry -> ResultErrorBuilder.withIllegalOption(entry.getKey(), entry.getValue()).build())
                        .forEach(builder::error);
            }
            if (customizer.isIncludeInvalidEnum()) {
                stream(result.getInvalidEnum())
                        .map(entry -> ResultErrorBuilder.withIllegalOption(entry.getKey(), entry.getValue())
                                .detail("enum.values", result.getEnumChoices(entry.getKey()))
                                .build())
                        .forEach(builder::error);
            }
        }
    }

    // *************************************
    // Helpers
    // *************************************

    protected <T> T setProperties(T instance, Map<String, Object> properties) throws Exception {
        if (camelContext == null) {
            throw new IllegalStateException("Camel context is null");
        }

        if (!properties.isEmpty()) {
            PropertyBindingSupport.build().bind(camelContext, instance, properties);
        }

        return instance;
    }

    protected <T> T setProperties(T instance, String prefix, Map<String, Object> properties) throws Exception {
        return setProperties(instance, PropertiesHelper.extractProperties(properties, prefix, false));
    }

    protected <T> Optional<T> getOption(Map<String, Object> parameters, String key, Class<T> type) {
        Object value = parameters.get(key);
        if (value != null) {
            return Optional.ofNullable(CamelContextHelper.convertTo(camelContext, type, value));
        }

        return Optional.empty();
    }

    protected <T> T getOption(Map<String, Object> parameters, String key, Class<T> type, Supplier<T> defaultSupplier) {
        return getOption(parameters, key, type).orElseGet(defaultSupplier);
    }

    protected <T> T getMandatoryOption(Map<String, Object> parameters, String key, Class<T> type) throws NoSuchOptionException {
        return getOption(parameters, key, type).orElseThrow(() -> new NoSuchOptionException(key));
    }
}
