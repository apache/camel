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
package org.apache.camel.dsl.yaml;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.junit.jupiter.api.Test;

class SimpleInitBlockTest extends YamlTestSupport {

    @Test
    void initBlock() throws Exception {
        loadRoutes("""
                - route:
                    from:
                      uri: direct:map
                      steps:
                        - setBody:
                            simple:
                              expression: |-
                                $init{
                                  // this is a java like comment
                                  $sum := ${sum(${header.lines},100)};

                                  $sku := ${iif(${body} contains 'Camel',123,999)};
                                }init$
                                orderId=$sku,total=$sum
                        - to:
                            uri: mock:result
                """);
        withMock("mock:result", mock -> {
            mock.expectedMessageCount(1);
            mock.message(0).body().contains("orderId=123,total=208");
        });

        context.start();

        withTemplate(t -> {
            t.to("direct:map").withHeader("lines", "75,33").withBody("Hello Camel").send();
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void initBlockCompactCustomFunction() throws Exception {
        loadRoutes("""
                - route:
                    from:
                      uri: direct:map
                      steps:
                        - setBody:
                            simple: |-
                              $init{
                                $foo ~:= ${uppercase()};
                              }init$
                              ${foo('hello')}
                        - to:
                            uri: mock:result
                """);
        withMock("mock:result", mock -> {
            mock.expectedMessageCount(1);
            mock.message(0).body().isEqualTo("HELLO");
        });

        context.start();

        withTemplate(t -> {
            t.to("direct:map").withBody("test").send();
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void initBlockCustomFunctionInDevProfile() throws Exception {
        context.getCamelContextExtension().setProfile("dev");
        loadRoutes("""
                - route:
                    from:
                      uri: direct:map
                      steps:
                        - setBody:
                            simple: |-
                              $init{
                                $foo ~:= ${uppercase()};
                              }init$
                              ${foo('hello')}
                        - to:
                            uri: mock:result
                """);
        withMock("mock:result", mock -> {
            mock.expectedMessageCount(1);
            mock.message(0).body().isEqualTo("HELLO");
        });

        context.start();

        withTemplate(t -> {
            t.to("direct:map").withBody("test").send();
        });

        MockEndpoint.assertIsSatisfied(context);
    }
}
