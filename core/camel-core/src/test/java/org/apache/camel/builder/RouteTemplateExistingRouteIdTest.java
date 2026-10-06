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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.FailedToCreateRouteFromTemplateException;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.TemplatedRouteDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A route created from a template must not replace an existing route with the same id.
 */
class RouteTemplateExistingRouteIdTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @BeforeEach
    void addRoutes() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate")
                        .templateParameter("foo")
                        .from("direct:{{foo}}")
                        .setBody(simple("template {{foo}}"));

                from("direct:existing").routeId("existing")
                        .setBody(constant("existing"));
            }
        });
        context.start();
    }

    @Test
    void builderRouteIdOfExistingRoute() {
        assertThatThrownBy(() -> TemplatedRouteBuilder.builder(context, "myTemplate")
                .routeId("existing")
                .parameter("foo", "one")
                .add())
                .isInstanceOf(FailedToCreateRouteFromTemplateException.class)
                .hasMessageContaining("existing");

        assertExistingRouteUnchanged();
        assertThat(context.hasEndpoint("direct:one")).isNull();
    }

    @Test
    void templatedRouteIdOfExistingRoute() {
        TemplatedRouteDefinition def = new TemplatedRouteDefinition();
        def.setRouteTemplateRef("myTemplate");
        def.setRouteId("existing");
        def.parameter("foo", "one");

        assertThatThrownBy(() -> ((ModelCamelContext) context).addRouteFromTemplatedRoute(def))
                .isInstanceOf(FailedToCreateRouteFromTemplateException.class)
                .hasMessageContaining("existing");

        assertExistingRouteUnchanged();
    }

    @Test
    void routeIdOfRouteFromSameTemplate() {
        TemplatedRouteBuilder.builder(context, "myTemplate").routeId("mine").parameter("foo", "one").add();

        assertThatThrownBy(() -> TemplatedRouteBuilder.builder(context, "myTemplate")
                .routeId("mine")
                .parameter("foo", "two")
                .add())
                .isInstanceOf(FailedToCreateRouteFromTemplateException.class)
                .hasMessageContaining("mine");

        assertThat(context.getRoutes()).hasSize(2);
        assertThat(template.requestBody("direct:one", "x")).isEqualTo("template one");
    }

    @Test
    void duplicateNodeIdWithoutRouteId() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("fixedNodeId")
                        .templateParameter("foo")
                        .from("direct:{{foo}}")
                        .setBody(constant("fixed")).id("fixed");
            }
        });
        TemplatedRouteBuilder.builder(context, "fixedNodeId").prefixId("p").parameter("foo", "one").add();

        // the same prefix gives the same node id, which is reported as such (also without a route id)
        assertThatThrownBy(() -> TemplatedRouteBuilder.builder(context, "fixedNodeId")
                .prefixId("p")
                .parameter("foo", "two")
                .add())
                .isInstanceOf(FailedToCreateRouteFromTemplateException.class)
                .hasMessageContaining("Duplicate id detected");
    }

    private void assertExistingRouteUnchanged() {
        assertThat(context.getRoutes()).hasSize(1);
        assertThat(context.getRouteDefinitions()).hasSize(1);
        assertThat(context.getRoute("existing").getEndpoint().getEndpointUri()).isEqualTo("direct://existing");
        assertThat(template.requestBody("direct:existing", "x")).isEqualTo("existing");
    }
}
