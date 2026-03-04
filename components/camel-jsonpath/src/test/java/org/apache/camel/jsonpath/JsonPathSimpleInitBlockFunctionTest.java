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
package org.apache.camel.jsonpath;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class JsonPathSimpleInitBlockFunctionTest extends CamelTestSupport {

    private final String MAPPING = """
            $init{
              $id := ${jsonpath($.id)};
              $type := ${header.type};
              $price := ${jsonpath($.amount)};
              $level ~:= ${body > 100 ? 'HIGH' : 'LOW'};
              $newStatus ~:= ${sum(${body},50)};
            }init$
            {
              "id": "$id",
              "type": "$type",
              "amount": $price,
              "oldStatus": $price ~> $level() ~> ${safeQuote()}
              "status": ${newStatus($price)} ~> $level() ~> ${safeQuote()}
            }
            """;

    private final String EXPECTED = """
            {
              "id": "123",
              "type": "silver",
              "amount": 78,
              "oldStatus": "LOW"
              "status": "HIGH"
            }""";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .transform().simple(MAPPING)
                        .log("${body}")
                        .to("mock:order");
            }
        };
    }

    @Test
    public void testMapping() throws Exception {
        getMockEndpoint("mock:order").expectedBodiesReceived(EXPECTED);

        template.sendBodyAndHeader("direct:start", """
                {
                  "id": 123,
                  "amount": 78
                }
                """, "type", "silver");

        MockEndpoint.assertIsSatisfied(context);
    }

}
