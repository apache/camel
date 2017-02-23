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
package org.apache.camel.impl.verifier;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ComponentVerifier;
import org.apache.camel.NoSuchOptionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.IntrospectionSupport;

public class DefaultComponentVerifier implements ComponentVerifier, CamelContextAware {
    public static final ComponentVerifier INSTANCE = new DefaultComponentVerifier();

    private CamelContext camelContext;

    public DefaultComponentVerifier() {
    }

    public DefaultComponentVerifier(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public Result verify(Scope scope, Map<String, Object> parameters) {
        switch (scope) {
        case PARAMETERS:
            return verifyParameters(parameters);
        case CONNECTIVITY:
            return verifyConnectivity(parameters);
        default:
            throw new IllegalArgumentException("Unsupported Verifier scope: " + scope);
        }
    }

    // *************************************
    // Implementation
    // *************************************

    protected Result verifyParameters(Map<String, Object> parameters) {
        return new ResultBuilder().scope(Scope.PARAMETERS).build();
    }

    protected Result verifyConnectivity(Map<String, Object> parameters) {
        return new ResultBuilder().scope(Scope.CONNECTIVITY).build();
    }

    // *************************************
    // Helpers
    // *************************************

    protected <T> T setProperties(T instance, Map<String, Object> properties) throws Exception {
        if (camelContext == null) {
            throw new IllegalStateException("Camel context is null");
        }

        if (!properties.isEmpty()) {
            final CamelContext context = getCamelContext();
            final TypeConverter converter = context.getTypeConverter();

            IntrospectionSupport.setProperties(converter, instance, properties);

            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                if (entry.getValue() instanceof String) {
                    String value = (String)entry.getValue();
                    if (EndpointHelper.isReferenceParameter(value)) {
                        IntrospectionSupport.setProperty(context, converter, instance, entry.getKey(), null, value, true);
                    }
                }
            }
        }

        return instance;
    }

    protected <T> T setProperties(T instance, String prefix, Map<String, Object> properties) throws Exception {
        return setProperties(
            instance,
            IntrospectionSupport.extractProperties(properties, prefix, false)
        );
    }

    protected <T> Optional<T> getOption(Map<String, Object> parameters, String key, Class<T> type) {
        Object value = parameters.get(key);
        if (value != null) {
            return Optional.ofNullable(CamelContextHelper.convertTo(getCamelContext(), type, value));
        }

        return Optional.empty();
    }

    protected <T> T getOption(Map<String, Object> parameters, String key, Class<T> type, Supplier<T> defaultSupplier) {
        return getOption(parameters, key, type).orElseGet(defaultSupplier);
    }

    protected <T> T getMandatoryOption(Map<String, Object> parameters, String key, Class<T> type) throws NoSuchOptionException {
        return getOption(parameters, key, type).orElseThrow(() ->  new NoSuchOptionException(key));
    }
}
