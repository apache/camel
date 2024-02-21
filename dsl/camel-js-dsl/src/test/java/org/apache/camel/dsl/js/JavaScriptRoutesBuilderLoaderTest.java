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
package org.apache.camel.dsl.js;

import org.apache.camel.component.seda.SedaComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.TransformDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaScriptRoutesBuilderLoaderTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "/routes/routes.js",
            "/routes/routes-with-endpoint-dsl.js",
    })
    void routesCanBeLoaded(String location) throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Resource resource = PluginHelper.getResourceLoader(context).resolveResource(location);
            PluginHelper.getRoutesLoader(context).loadRoutes(resource);

            assertThat(context.getRouteDefinitions())
                    .hasSize(1)
                    .first()
                    .satisfies(rd -> {
                        assertThat(rd.getInput().getEndpointUri()).matches("timer:.*tick");
                        assertThat(rd.getOutputs().get(0)).isInstanceOf(ToDefinition.class);
                    });
        }
    }

    @Test
    void componentsCanBeCustomized() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Resource resource = PluginHelper.getResourceLoader(context)
                    .resolveResource("/routes/routes-with-component-configuration.js");
            PluginHelper.getRoutesLoader(context).loadRoutes(resource);

            assertThat(context.getComponent("seda", SedaComponent.class)).satisfies(c -> {
                assertThat(c.getQueueSize()).isEqualTo(1234);
            });
        }
    }

    @Test
    void contextCanBeCustomized() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Resource resource = PluginHelper.getResourceLoader(context)
                    .resolveResource("/routes/routes-with-context-configuration.js");
            PluginHelper.getRoutesLoader(context).loadRoutes(resource);

            assertThat(context.isTypeConverterStatisticsEnabled()).isTrue();
        }
    }

    @Test
    void processorsCanBeCreated() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Resource resource = PluginHelper.getResourceLoader(context).resolveResource("/routes/routes-with-processors.js");
            PluginHelper.getRoutesLoader(context).loadRoutes(resource);

            context.start();

            assertThat(context.createFluentProducerTemplate().to("direct:arrow").request(String.class))
                    .isEqualTo("arrow");
            assertThat(context.createFluentProducerTemplate().to("direct:wrapper").request(String.class))
                    .isEqualTo("wrapper");
            assertThat(context.createFluentProducerTemplate().to("direct:function").request(String.class))
                    .isEqualTo("function");
        }
    }

    @Test
    void restCanBeConfigured() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Resource resource = PluginHelper.getResourceLoader(context)
                    .resolveResource("/routes/routes-with-rest-configuration.js");
            PluginHelper.getRoutesLoader(context).loadRoutes(resource);

            assertThat(context.getRestConfiguration()).satisfies(c -> {
                assertThat(c.getComponent()).isEqualTo("undertow");
                assertThat(c.getPort()).isEqualTo(1234);
            });
        }
    }

    @Test
    void restDslCanBeDefined() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Resource resource = PluginHelper.getResourceLoader(context).resolveResource("/routes/routes-with-rest-dsl.js");
            PluginHelper.getRoutesLoader(context).loadRoutes(resource);

            assertThat(context.getRestDefinitions()).hasSize(1);
            assertThat(context.getRouteDefinitions()).hasSize(2);

            assertThat(context.getRestDefinitions()).first().satisfies(d -> {
                assertThat(d.getProduces()).isEqualTo("text/plain");
                assertThat(d.getVerbs()).first().satisfies(v -> {
                    assertThat(v.getPath()).isEqualTo("/say/hello");
                });
            });
            assertThat(context.getRouteDefinitions()).first().satisfies(d -> {
                assertThat(d.getInput()).isInstanceOf(FromDefinition.class);
                assertThat(d.getOutputs()).first().isInstanceOf(TransformDefinition.class);
            });
        }
    }

    @Test
    void modulesCanBeImported() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Resource resource = PluginHelper.getResourceLoader(context).resolveResource("/routes/routes-with-modules.js");
            PluginHelper.getRoutesLoader(context).loadRoutes(resource);

            assertThat(context.getRouteDefinitions()).hasSize(1);
        }
    }
}
