/**
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
package org.apache.camel.builder.xml;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class XPathNestedNamespaceTest extends ContextTestSupport {

    public void testXPathNamespace() throws Exception {
        String xml = "<STPTradeML xmlns=\"http://www.barcap.com/gcd/stpengine/1-0\"><FpML><trade/></FpML></STPTradeML>";

        getMockEndpoint("mock:FOO").expectedMessageCount(0);
        getMockEndpoint("mock:BAR").expectedMessageCount(1);
        getMockEndpoint("mock:OTHER").expectedMessageCount(0);

        template.sendBody("direct:start", xml);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Namespaces ns = new Namespaces("stp", "http://www.barcap.com/gcd/stpengine/1-0");

                from("direct:start")
                    .choice()
                        .when().xpath("//stp:termination", ns)
                            .to("mock:FOO")
                        .when().xpath("/stp:STPTradeML/stp:FpML/stp:trade", ns)
                            .to("mock:BAR")
                        .otherwise()
                            .to("mock:OTHER");
            }
        };
    }

}
