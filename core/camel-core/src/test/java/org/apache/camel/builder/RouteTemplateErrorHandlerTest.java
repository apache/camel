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
package org.apache.camel.builder;

import org.apache.camel.Channel;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultRoute;
import org.apache.camel.model.errorhandler.NoErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.RefErrorHandlerDefinition;
import org.apache.camel.processor.errorhandler.NoErrorHandler;
import org.junit.jupiter.api.Test;

import static org.apache.camel.TestSupport.unwrapChannel;
import static org.apache.camel.util.CollectionHelper.mapOf;
import static org.assertj.core.api.Assertions.assertThat;

public class RouteTemplateErrorHandlerTest {
    @Test
    public void testDefineRouteTemplate() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getCamelContextExtension().getRegistry().bind("myErrorHandler", new NoErrorHandlerDefinition());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    errorHandler("myErrorHandler");

                    routeTemplate("myTemplate")
                            .templateParameter("foo")
                            .templateParameter("bar")
                            .from("direct:{{foo}}")
                            .to("mock:{{bar}}");

                    from("direct:_r")
                            .to("mock:_r");
                }
            });

            assertThat(context.getRouteDefinitions()).first().satisfies(d -> {
                assertThat(d.getErrorHandlerFactory()).isInstanceOfSatisfying(RefErrorHandlerDefinition.class, h -> {
                    assertThat(h.getRef()).isEqualTo("myErrorHandler");
                });
            });

            assertThat(context.getRouteTemplateDefinitions()).first().satisfies(d -> {
                assertThat(d.route().getErrorHandlerFactory()).isInstanceOfSatisfying(RefErrorHandlerDefinition.class, h -> {
                    assertThat(h.getRef()).isEqualTo("myErrorHandler");
                });
            });

            context.addRouteFromTemplate("myId", "myTemplate", mapOf("foo", "f", "bar", "b"));
            context.start();

            assertThat(context.getRoutes()).allSatisfy(r -> {
                assertThat(r).isInstanceOfSatisfying(DefaultRoute.class, dr -> {
                    Channel channel = unwrapChannel(r.getProcessor());
                    assertThat(channel).isNotNull();
                    assertThat(channel.getErrorHandler()).isInstanceOf(NoErrorHandler.class);
                });
            });
        }
    }
}
