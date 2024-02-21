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
package org.apache.camel.language;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class SimpleXPathTransformTest extends ContextTestSupport {

    private static String EXPECTED = """
            <user>
              <rool>123</rool>
              <country>sweden</country>
              <fullname>scott</fullname>
            </user>""";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .transform().simple("""
                                <user>
                                  <rool>${xpath(/order/@id)}</rool>
                                  <country>${xpath(/order/address/co/text())}</country>
                                  <fullname>${xpath(/order/first/text())}</fullname>
                                </user>""")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testTransform() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(EXPECTED);

        template.sendBody("direct:start", """
                <order id="123">
                  <item>Brake</item>
                  <first>scott</first>
                  <last>jackson</last>
                  <address>
                    <co>sweden</co>
                    <zip>12345</zip>
                  </address>
                </order>
                """);

        MockEndpoint.assertIsSatisfied(context);
    }

}
