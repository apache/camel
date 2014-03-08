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
package org.apache.camel.test.patterns;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

public class DebugSpringTest extends CamelSpringTestSupport {
    private boolean debugged;
    
    @Override
    public boolean isUseDebugger() {
        // must enable debugger
        return true;
    }

    @Override
    protected void debugBefore(Exchange exchange, Processor processor,
                               ProcessorDefinition<?> definition, String id, String shortName) {
        // this method is invoked before we are about to enter the given processor
        // from your Java editor you can just add a breakpoint in the code line below
        log.info("Before " + definition + " with body " + exchange.getIn().getBody());
        debugged = true;
    }


    @Test
    public void testDebugger() throws Exception {
        // set mock expectations
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);

        // send a message
        template.sendBody("direct:start", "World");

        // assert mocks
        assertMockEndpointsSatisfied();
        assertTrue("The debugger is not called!", debugged);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // this is the route we want to debug
                from("direct:start")
                    .to("mock:a")
                    .transform(body().prepend("Hello "))
                    .to("mock:b");
            }
        };
    }
    

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new GenericApplicationContext();
    }

}
