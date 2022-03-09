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

import java.util.UUID;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class KameletRouteTest extends CamelTestSupport {
    @Test
    public void testSingle() {
        String body = UUID.randomUUID().toString();

        assertThat(
                fluentTemplate.toF("direct:single").withBody(body).request(String.class)).isEqualTo("a-" + body);
    }

    @Test
    public void testChain() {
        String body = UUID.randomUUID().toString();

        assertThat(
                fluentTemplate.toF("direct:chain").withBody(body).request(String.class)).isEqualTo("b-a-" + body);
    }

    @Test
    public void duplicateRouteId() {
        RouteBuilder rb = new RouteBuilder(context) {
            @Override
            public void configure() {
                from("direct:start")
                        .to("kamelet:echo/test?prefix=test");
            }
        };

        assertThrows(FailedToCreateRouteException.class, () -> rb.addRoutesToCamelContext(context));
    }

    // **********************************************
    //
    // test set-up
    //
    // **********************************************

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("echo")
                        .templateParameter("prefix")
                        .from("kamelet:source")
                        .setBody().simple("{{prefix}}-${body}");

                from("direct:single").routeId("test")
                        .to("kamelet:echo?prefix=a")
                        .log("${body}");

                from("direct:chain")
                        .to("kamelet:echo/1?prefix=a")
                        .to("kamelet:echo/2?prefix=b")
                        .log("${body}");
            }
        };
    }
}
