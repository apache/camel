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
package org.apache.camel;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class ValidatorExternalResourceTest extends ContextTestSupport {

    @Test
    public void testExternalResource() throws InterruptedException {
        final MockEndpoint mock = getMockEndpoint("mock:out");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "<ord:order  xmlns:ord=\"http://example.org/ord\"\n" +
                                          "   xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                                          "   xsi:schemaLocation=\"http://example.org/ord order.xsd\">\n" +
                                          "  <customer>\n" +
                                          "    <name>Priscilla Walmsley</name>\n" +
                                          "    <number>12345</number>\n" +
                                          "  </customer>\n" +
                                          "  <items>\n" +
                                          "    <product>\n" +
                                          "      <number>98765</number>\n" +
                                          "      <name>Short-Sleeved Linen Blouse</name>\n" +
                                          "      <size system=\"US-DRESS\">10</size>\n" +
                                          "      <color value=\"blue\"/>\n" +
                                          "    </product>\n" +
                                          "  </items>\n" +
                                          "</ord:order>");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .toD("validator:https://raw.githubusercontent.com/apache/camel/main/core/camel-core/src/test/resources/org/apache/camel/component/validator/xsds/order.xsd")
                        .to("mock:out");
            }
        };
    }
}
