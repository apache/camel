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
package org.apache.camel.dsl.java.joor;

import java.util.Collection;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.java.joor.support.MyUser;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ProcessDefinition;
import org.apache.camel.model.SetBodyDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaRoutesBuilderLoaderTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/routes/MyRoutes.java",
            "/routes/MyRoutesWithPackage.java",
            "/routes/MyRoutesWithPackageAndComment.java",
            "/routes/MyRoutesWithPackageAndLineComment.java"
    })
    void testLoadRoutes(String location) throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Resource resource = PluginHelper.getResourceLoader(context).resolveResource(location);
            Collection<RoutesBuilder> builders = PluginHelper.getRoutesLoader(context).findRoutesBuilders(resource);

            assertThat(builders).hasSize(1);

            RouteBuilder builder = (RouteBuilder) builders.iterator().next();
            builder.setCamelContext(context);
            builder.configure();

            Assertions.assertThat(builder.getRouteCollection().getRoutes())
                    .hasSize(1)
                    .first()
                    .satisfies(rd -> {
                        Assertions.assertThat(rd.getInput().getEndpointUri()).matches("timer:.*tick");
                        Assertions.assertThat(rd.getOutputs().get(0)).isInstanceOf(ToDefinition.class);
                    });
        }
    }

    @Test
    void testLoadRoutesWithNestedClass() throws Exception {
        final String location = "/routes/MyRoutesWithNestedClass.java";

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Resource resource = PluginHelper.getResourceLoader(context).resolveResource(location);
            Collection<RoutesBuilder> builders = PluginHelper.getRoutesLoader(context).findRoutesBuilders(resource);

            assertThat(builders).hasSize(1);

            RouteBuilder builder = (RouteBuilder) builders.iterator().next();
            builder.setCamelContext(context);
            builder.configure();

            Assertions.assertThat(builder.getRouteCollection().getRoutes())
                    .hasSize(1)
                    .first()
                    .satisfies(rd -> {
                        Assertions.assertThat(rd.getInput().getEndpointUri()).matches("timer:.*tick");
                        Assertions.assertThat(rd.getOutputs().get(0)).isInstanceOf(SetBodyDefinition.class);
                        Assertions.assertThat(rd.getOutputs().get(1)).isInstanceOf(ProcessDefinition.class);
                        Assertions.assertThat(rd.getOutputs().get(2)).isInstanceOf(ToDefinition.class);
                    });
        }
    }

    @Test
    void testLoadRoutesWithRestConfiguration() throws Exception {
        final String location = "/routes/MyRoutesWithRestConfiguration.java";

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Resource resource = PluginHelper.getResourceLoader(context).resolveResource(location);
            Collection<RoutesBuilder> builders = PluginHelper.getRoutesLoader(context).findRoutesBuilders(resource);

            assertThat(builders).hasSize(1);

            RouteBuilder builder = (RouteBuilder) builders.iterator().next();
            builder.setCamelContext(context);
            builder.configure();

            Assertions.assertThat(builder.getRestConfiguration())
                    .hasFieldOrPropertyWithValue("component", "restlet");
        }
    }

    @Test
    void testLoadRoutesWithModel() throws Exception {
        final String location = "/routes/MyRoutesWithModel.java";

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Resource resource = PluginHelper.getResourceLoader(context).resolveResource(location);
            Collection<RoutesBuilder> builders = PluginHelper.getRoutesLoader(context).findRoutesBuilders(resource);

            assertThat(builders).hasSize(1);

            RouteBuilder builder = (RouteBuilder) builders.iterator().next();
            builder.setCamelContext(context);
            builder.configure();

            Assertions.assertThat(builder.getRestCollection().getRests()).anySatisfy(rd -> {
                Assertions.assertThat(rd.getVerbs())
                        .first()
                        .hasFieldOrPropertyWithValue("outType", MyUser.class.getName());
            });
        }
    }
}
