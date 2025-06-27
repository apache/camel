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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EnrichIdTest extends ContextTestSupport {

    @Test
    public void testEnrichId() throws Exception {
        Assertions.assertInstanceOf(Enricher.class, context.getProcessor("Enriching something"));
        Assertions.assertInstanceOf(LogProcessor.class, context.getProcessor("After enrich"));
        Assertions.assertInstanceOf(SetBodyProcessor.class, context.getProcessor("Setting body"));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("myRoute")
                    .choice()
                        .when().constant(true)
                            .enrich()
                                .constant("direct:test")
                            .end()
                            .id("Enriching something")
                        .end()
                        .log("${body}").id("After enrich");

                from("direct:test")
                        .setBody(constant("I'm running")).id("Setting body");
            }
        };
    }
}
