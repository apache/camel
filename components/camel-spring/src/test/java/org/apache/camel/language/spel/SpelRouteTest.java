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
package org.apache.camel.language.spel;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import static org.apache.camel.language.spel.SpelExpression.spel;

public class SpelRouteTest extends ContextTestSupport {
    
    public void testSpelRoute() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedBodiesReceived("Hello World! What a beautiful day");
        template.sendBodyAndHeader("direct:test", "World", "dayOrNight", "day");
        resultEndpoint.assertIsSatisfied();
    }
    
    public void testSpelWithLoop() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:loopResult");
        resultEndpoint.expectedBodiesReceived("A:0", "A:0:1", "A:0:1:2", "A:0:1:2:3");
        template.sendBody("direct:loop", "A");
        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:test").setBody(spel("Hello #{request.body}! What a beautiful #{request.headers['dayOrNight']}")).to("mock:result");
                from("direct:loop").loop(4).setBody(spel("#{body + ':' + properties['CamelLoopIndex']}")).to("mock:loopResult");
            }
        };
    }
}
