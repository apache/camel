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
package org.apache.camel.component.language;

import java.net.URLEncoder;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class LanguageRouteConvertBodyTest extends ContextTestSupport {

    public void testLanguage() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("123;Camel in Action");

        template.sendBody("direct:start", new MyOrder(123, "Camel in Action"));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                String script = URLEncoder.encode("${mandatoryBodyAs(String)}", "UTF-8");
                from("direct:start").to("language:simple:" + script).to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

    private static final class MyOrder {
        private int id;
        private String name;

        private MyOrder(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return id + ";" + name;
        }
    }
}
