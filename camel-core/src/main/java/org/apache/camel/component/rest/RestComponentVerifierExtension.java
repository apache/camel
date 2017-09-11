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
import java.util.Map;
import java.util.Optional;

import org.apache.camel.Component;
import org.apache.camel.ComponentVerifier;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.CatalogVerifierCustomizer;
import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;
import org.apache.camel.runtimecatalog.JSonSchemaHelper;
import org.apache.camel.runtimecatalog.RuntimeCamelCatalog;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.function.Suppliers;

public class RestComponentVerifierExtension extends DefaultComponentVerifierExtension implements ComponentVerifier {
    private static final CatalogVerifierCustomizer CUSTOMIZER = new CatalogVerifierCustomizer().excludeUnknown();

    RestComponentVerifierExtension() {
        super("rest");
    }

    // *********************************
    // Parameters validation
    // *********************************

    @Override
    protected Result verifyParameters(Map<String, Object> parameters) {
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS);

        // Validate using the catalog but do not report unknown options as error
        // as the may be used to customize the underlying component
        super.verifyParametersAgainstCatalog(builder, parameters, CUSTOMIZER);

        verifyUnderlyingComponent(Scope.PARAMETERS, builder, parameters);

        return builder.build();
    }

    // *********************************
    // Connectivity validation
    // *********************************

    @Override
    protected Result verifyConnectivity(Map<String, Object> parameters) {
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.CONNECTIVITY);

        verifyUnderlyingComponent(Scope.CONNECTIVITY, builder, parameters);

        return builder.build();
    }

    // *********************************
    // Helpers
    // *********************************

    protected void verifyUnderlyingComponent(Scope scope, ResultBuilder builder, Map<String, Object> parameters) {
        // componentName is required for validation even at runtime camel might
        // be able to find a suitable component at runtime.
        String componentName = (String)parameters.get("componentName");
        if (ObjectHelper.isNotEmpty(componentName)) {
            try {
                final Component component = getTransportComponent(componentName);
                final Optional<ComponentVerifierExtension> extension = component.getExtension(ComponentVerifierExtension.class);

                if (extension.isPresent()) {
                    final ComponentVerifierExtension verifier = extension.get();
                    final RuntimeCamelCatalog catalog = getCamelContext().getRuntimeCamelCatalog();
                    final String json = catalog.componentJSonSchema("rest");
                    final Map<String, Object> restParameters = new HashMap<>(parameters);

                    for (Map<String, String> m : JSonSchemaHelper.parseJsonSchema("componentProperties", json, true)) {
                        String name = m.get("name");
                        Object val = restParameters.remove(name);
                        if (val != null) {
                            // Add rest prefix to properties belonging to the rest
                            // component so the underlying component know we want
                            // to validate rest-related stuffs.
                            restParameters.put("rest." + name, parameters.get(name));
                        }
                    }
                    for (Map<String, String> m : JSonSchemaHelper.parseJsonSchema("properties", json, true)) {
                        String name = m.get("name");
                        Object val = restParameters.remove(name);
                        if (val != null) {
                            // Add rest prefix to properties belonging to the rest
                            // component so the underlying component know we want
                            // to validate rest-related stuffs.
                            restParameters.put("rest." + name, parameters.get(name));
                        }
                    }

                    // restParameters now should contains rest-component related
                    // properties with "rest." prefix and all the remaining can
                    // be used to customize the underlying component (i.e. http
                    // proxies, auth, etc)
                    Result result = verifier.verify(scope, restParameters);

                    // Combine errors and add an information about the component
                    // they comes from
                    for (VerificationError error : result.getErrors()) {
                        builder.error(
                            ResultErrorBuilder.fromError(error)
                                .detail("component", componentName)
                                .build()
                        );
                    }
                } else {
                    builder.error(
                        ResultErrorBuilder.withUnsupportedComponent(componentName).build()
                    );
                }
            } catch (Exception e) {
                builder.error(
                    ResultErrorBuilder.withException(e).build()
                );
            }
        } else {
            builder.error(ResultErrorBuilder.withMissingOption("componentName").build());
        }
    }

    private Component getTransportComponent(String componentName) throws Exception {
        return Suppliers.firstMatching(
            comp -> comp != null && (comp instanceof RestConsumerFactory || comp instanceof RestProducerFactory),
            () -> getCamelContext().getRegistry().lookupByNameAndType(componentName, Component.class),
            () -> getCamelContext().getComponent(componentName, true, false)
        ).orElse(null);
    }
}
