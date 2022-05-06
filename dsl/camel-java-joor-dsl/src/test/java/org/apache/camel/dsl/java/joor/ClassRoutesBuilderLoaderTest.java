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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.ResourceSupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassRoutesBuilderLoaderTest {

    @Test
    void testLoadRoutes() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Resource resource = new ResourceSupport("class", "org/apache/camel/dsl/java/joor/DummyRoute.class") {
                @Override
                public boolean exists() {
                    return true;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return ClassRoutesBuilderLoaderTest.class
                            .getResourceAsStream("/org/apache/camel/dsl/java/joor/DummyRoute.class");
                }
            };
            Collection<RoutesBuilder> builders = context.getRoutesLoader().findRoutesBuilders(resource);

            assertThat(builders).hasSize(1);

            RouteBuilder builder = (RouteBuilder) builders.iterator().next();
            builder.setCamelContext(context);
            builder.configure();

            Assertions.assertThat(builder.getRouteCollection().getRoutes())
                    .hasSize(1)
                    .first()
                    .satisfies(rd -> {
                        Assertions.assertThat(rd.getInput().getEndpointUri()).matches("direct:dummy");
                        Assertions.assertThat(rd.getOutputs().get(0)).isInstanceOf(ToDefinition.class);
                    });
        }
    }

}
