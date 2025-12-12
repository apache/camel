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

public class JsonPathSimpleInitBlockTest extends CamelTestSupport {

    private final String MAPPING = """
            $init{
              $id := ${jsonpath($.id)}
              $type := ${header.type}
              $price := ${jsonpath($.amount)}
              $level := ${iif(${jsonpath($.amount)} > 100,HIGH,LOW)}
            }init$
            {
              "id": "$id",
              "type": "$type",
              "amount": $price,
              "status": "$level"
            }
            """;

    private final String MAPPING2 = """
            $init{
              $id := ${jsonpath($.id)}
              $type := ${header.type}
              $price := ${jsonpath($.amount)}
              $level := ${iif(${jsonpath($.amount)} > 100,HIGH,LOW)}
            }init$
            {
              "id": "$id",
              "type": "$type",
              "amount": ${jsonpath($.amount)},
              "status": "${lowercase($level)}"
            }
            """;

    private final String EXPECTED = """
            {
              "id": "123",
              "type": "silver",
              "amount": 44,
              "status": "LOW"
            }""";

    private final String EXPECTED2 = """
            {
              "id": "456",
              "type": "gold",
              "amount": 888,
              "status": "high"
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

                from("direct:start2")
                        .transform().simple(MAPPING2)
                        .log("${body}")
                        .to("mock:order2");
            }
        };
    }

    @Test
    public void testMapping() throws Exception {
        getMockEndpoint("mock:order").expectedBodiesReceived(EXPECTED);

        template.sendBodyAndHeader("direct:start", """
                {
                  "id": 123,
                  "amount": 44
                }
                """, "type", "silver");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMapping2() throws Exception {
        getMockEndpoint("mock:order2").expectedBodiesReceived(EXPECTED2);

        template.sendBodyAndHeader("direct:start2", """
                {
                  "id": 456,
                  "amount": 888
                }
                """, "type", "gold");

        MockEndpoint.assertIsSatisfied(context);
    }

}
