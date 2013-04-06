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
package org.apache.camel.language.groovy;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class GroovySetHeaderTest extends CamelTestSupport {

    @Test
    public void testSetHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("one", "einz");
        mock.expectedHeaderReceived("two", "twei");
        mock.expectedHeaderReceived("beer", "Carlsberg");
        mock.expectedHeaderReceived("drink", "Carlsberg");
        mock.expectedHeaderReceived("camelId", context.getName());

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("one", "einz");
        headers.put("two", "twei");
        headers.put("beer", "Carlsberg");

        template.sendBodyAndHeaders("direct:start", "Hello World", headers);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .setHeader("drink").groovy("request.headers.beer")
                    // shows how to access the camelContext value
                    .setHeader("camelId").groovy("camelContext.name")
                    .to("mock:result");
            }
        };
    }
}
