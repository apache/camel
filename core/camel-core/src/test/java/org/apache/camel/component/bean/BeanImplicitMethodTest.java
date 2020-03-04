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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class BeanImplicitMethodTest extends ContextTestSupport {

    @Test
    public void testRoute() throws Exception {

        String stringBody = "stringBody";
        String stringResponse = (String)template.requestBody("direct:in", stringBody);
        assertEquals(stringBody, stringResponse);

        Integer intBody = 1;
        Integer intResponse = (Integer)template.requestBody("direct:in", intBody);
        assertEquals(1, intResponse.intValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in").to("bean:myBean");
            }
        };
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();
        answer.bind("myBean", new MyBean());
        return answer;
    }

    public static class MyBean {

        public Integer intRequest(Integer request) {
            return request;
        }

        public String stringRequest(String request) {
            return request;
        }

    }

}
