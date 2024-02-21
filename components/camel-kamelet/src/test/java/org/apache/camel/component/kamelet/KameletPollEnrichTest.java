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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class KameletPollEnrichTest extends CamelTestSupport {

    @Test
    public void testPollEnrich() throws Exception {
        template.sendBody("seda:foo", "AA");
        template.sendBody("seda:bar", "Hello B");

        String out = template.requestBody("direct:foo", "A", String.class);
        Assertions.assertEquals("AA", out);

        out = template.requestBody("direct:bar", "B", String.class);
        Assertions.assertEquals("Hello B", out);

        MockEndpoint.assertIsSatisfied(context);
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
                routeTemplate("broker")
                        .templateParameter("queue")
                        .from("kamelet:source")
                        .pollEnrich().simple("seda:{{queue}}").timeout(5000);

                from("direct:foo")
                        .kamelet("broker?queue=foo");

                from("direct:bar")
                        .kamelet("broker?queue=bar");
            }
        };
    }
}
