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
package org.apache.camel.java;

import java.io.IOException;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.ThrowExceptionDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LwModelToJavaDumperTest {

    @Test
    public void testDumpRouteTemplateRestAndRouteConfiguration() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("tpl").templateParameter("name").from("direct:{{name}}").to("mock:result");

                    rest("/api").get("/hello").to("direct:hello");
                }
            });
            context.addRoutes(new RouteConfigurationBuilder() {
                @Override
                public void configuration() {
                    routeConfiguration("cfg").onException(IllegalArgumentException.class).handled(true);
                }
            });
            Model model = context.getCamelContextExtension().getContextPlugin(Model.class);
            LwModelToJavaDumper dumper = new LwModelToJavaDumper();

            RouteTemplateDefinition template = model.getRouteTemplateDefinition("tpl");
            assertThat(dumper.dumpModelAsJava(context, template)).startsWith("routeTemplate(\"tpl\")");

            RestDefinition rest = model.getRestDefinitions().get(0);
            assertThat(dumper.dumpModelAsJava(context, rest)).startsWith("rest(\"/api\")");

            RouteConfigurationDefinition config = model.getRouteConfigurationDefinitions().get(0);
            assertThat(dumper.dumpModelAsJava(context, config)).startsWith("routeConfiguration(\"cfg\")");
        }
    }

    @Test
    public void testDumpMultiLineExpression() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").routeId("myRoute").setBody(simple("Hello\n  ${body}")).to("mock:result");
                }
            });
            String java = new LwModelToJavaDumper().dumpModelAsJava(context, context.getRouteDefinition("myRoute"));
            assertThat(java).contains("\"Hello\\n  ${body}\"");
        }
    }

    /**
     * A route built in Java keeps expression clauses, classes and arrays the XML DSL does not have: they are written as
     * Java DSL, without changing the model (CAMEL-25157).
     */
    @Test
    public void testDumpRouteBuiltInJava() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").routeId("javaBuilt")
                            .setProperty("p").constant("v")
                            .throwException(IllegalArgumentException.class, "Forced")
                            .loadBalance().failover(IOException.class).to("mock:x").end()
                            .removeHeaders("*", "Keep*")
                            .to("mock:result");
                }
            });
            String java = new LwModelToJavaDumper().dumpModelAsJava(context, context.getRouteDefinition("javaBuilt"));
            assertThat(java).contains(
                    ".setProperty(\"p\", constant(\"v\"))",
                    ".throwException(IllegalArgumentException.class, \"Forced\")",
                    ".failover(java.io.IOException.class)",
                    ".removeHeaders(\"*\", \"Keep*\")");
            // the model was not changed
            assertThat(((ThrowExceptionDefinition) context.getRouteDefinition("javaBuilt").getOutputs().get(1))
                    .getExceptionType()).isNull();
        }
    }
}
