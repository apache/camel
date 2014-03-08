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
package org.apache.camel.builder.saxon;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 *
 */
public class SaxonXPathTest extends CamelTestSupport {

    @Test
    public void testSaxonXPath() throws Exception {
        getMockEndpoint("mock:london").expectedMessageCount(1);
        getMockEndpoint("mock:paris").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(1);

        template.sendBody("direct:start", "<person><city>London</city></person>");
        template.sendBody("direct:start", "<person><city>Berlin</city></person>");
        template.sendBody("direct:start", "<person><city>Paris</city></person>");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .choice()
                        .when().xpath("person/city = 'London'")
                            .to("mock:london")
                        .when().xpath("person/city = 'Paris'")
                            .to("mock:paris")
                        .otherwise()
                            .to("mock:other");
            }
        };
    }


}
