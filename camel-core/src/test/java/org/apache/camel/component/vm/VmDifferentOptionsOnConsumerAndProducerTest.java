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
package org.apache.camel.component.vm;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.ServiceHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @version 
 */
public class VmDifferentOptionsOnConsumerAndProducerTest extends ContextTestSupport {
    
    private CamelContext context2;
    private ProducerTemplate template2;
    
    @Override
    @Before
    protected void setUp() throws Exception {
        super.setUp();
        
        context2 = new DefaultCamelContext();
        context2.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("vm:foo");
            }
        });
        
        template2 = context2.createProducerTemplate();
        
        ServiceHelper.startServices(template2, context2);
    }
    
    @Override
    @After
    protected void tearDown() throws Exception {
        ServiceHelper.stopServices(context2, template2);
        
        super.tearDown();
    }

    @Test
    public void testSendToVm() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template2.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("vm:foo?concurrentConsumers=5")
                    .to("mock:result");
            }
        };
    }
}