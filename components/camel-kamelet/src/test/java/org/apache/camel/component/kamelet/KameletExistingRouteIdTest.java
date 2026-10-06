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
package org.apache.camel.component.kamelet;

import org.apache.camel.FailedToCreateRouteFromTemplateException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Two Kamelet endpoints with the same route id must not replace each other's route.
 */
class KameletExistingRouteIdTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void sameRouteIdWithOtherParameters() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("echo")
                        .templateParameter("prefix")
                        .from("kamelet:source")
                        .setBody().simple("{{prefix}}-${body}");
            }
        });
        context.start();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").routeId("a").to("kamelet:echo/same?prefix=a");
            }
        });
        assertThat(template.requestBody("direct:a", "x")).isEqualTo("a-x");

        assertThatThrownBy(() -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:b").routeId("b").to("kamelet:echo/same?prefix=b");
            }
        })).hasRootCauseInstanceOf(FailedToCreateRouteFromTemplateException.class);

        // the route of the first Kamelet endpoint is kept
        assertThat(template.requestBody("direct:a", "x")).isEqualTo("a-x");
    }
}
