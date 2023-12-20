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

import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Expression;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.language.constant.ConstantLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SetHeadersProcessorTest extends ContextTestSupport {

    public static class HeaderBean {
        Map<String, String> map = new java.util.LinkedHashMap<>();

        public HeaderBean() {
        }

        Map<String, String> getHeaders(String body) {
            map.clear();
            String[] mapInfo = body.split(",");
            for (int i = 0; i < mapInfo.length; i += 2) {
                map.put(mapInfo[i], mapInfo[i + 1]);
            }
            return map;
        }
    }

    private Map<String, Expression> headerMap = new java.util.LinkedHashMap<>();
    protected String body = "<person name='Jane' age='10'/>";
    protected MockEndpoint expected;

    @Test
    public void testUseConstantParameters() throws Exception {
        expected.message(0).header("foo").isEqualTo("ABC");
        expected.message(0).header("bar").isEqualTo("XYZ");

        template.sendBodyAndHeader("direct:startConstant", body, "bar", "ABC");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUseSimpleLanguage() throws Exception {
        expected.message(0).header("foo").isEqualTo("ABC");
        expected.message(0).header("bar").isEqualTo("XYZ");

        template.sendBodyAndHeader("direct:start", "ABC", "bar1", "XYZ");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUseXpathLanguage() throws Exception {
        expected.message(0).header("name").isEqualTo("Jane");
        expected.message(0).header("age").isEqualTo(10);

        template.sendBody("direct:startXpath", body);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUseMap() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:startMap").setHeaders(headerMap).to("mock:result");
            }
        });
        ;
        expected.message(0).header("foo").isEqualTo("ABC");
        expected.message(0).header("bar").isEqualTo("XYZ");
        template.sendBody("direct:startMap", body);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUseMapOf() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:startMap").setHeaders(Map.of("foo", "ABC", "bar", "XYZ")).to("mock:result");
            }
        });
        ;
        expected.message(0).header("foo").isEqualTo("ABC");
        expected.message(0).header("bar").isEqualTo("XYZ");
        template.sendBody("direct:startMap", body);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUseMethod() throws Exception {
        String hdrInBody = "foo,ABC,bar,XYZ";
        template.sendBody("direct:startMethod", hdrInBody);
        expected.getExchanges().get(0);
        expected.message(0).header("mapTest").isEqualTo(new HeaderBean().getHeaders(hdrInBody));
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSetOneHeaderFromAnother() throws Exception {
        expected.message(0).header("foo").isEqualTo(15);
        expected.message(0).header("bar").isEqualTo(true);
        template.sendBody("direct:startDepHeader", 15);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").setHeaders("foo", simple("${body}"),
                        "bar", simple("${header.bar1}")).to("mock:result");
                from("direct:startDepHeader").setHeaders("foo", simple("${body}"),
                        "bar", simple("${header.foo} > 10", Boolean.class)).to("mock:result");
                from("direct:startConstant").setHeaders("foo", constant("ABC"),
                        "bar", constant("XYZ")).to("mock:result");
                from("direct:startXpath").setHeaders("age", xpath("/person/@age"),
                        "name", xpath("/person/@name")).to("mock:result");
                from("direct:startMethod").setHeaders("mapTest", method(HeaderBean.class, "getHeaders")).to("mock:result");
            }
        };
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        headerMap.put("foo", ConstantLanguage.constant("ABC"));
        headerMap.put("bar", ConstantLanguage.constant("XYZ"));
        expected = getMockEndpoint("mock:result");
    }
}
