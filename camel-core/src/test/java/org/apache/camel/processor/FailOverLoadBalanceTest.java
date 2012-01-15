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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import static org.apache.camel.component.mock.MockEndpoint.expectsMessageCount;

public class FailOverLoadBalanceTest extends ContextTestSupport {

    protected MockEndpoint x;
    protected MockEndpoint y;
    protected MockEndpoint z;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        x = getMockEndpoint("mock:x");
        y = getMockEndpoint("mock:y");
        z = getMockEndpoint("mock:z");
    }
    
    public static class MyException extends Exception {
        private static final long serialVersionUID = 1L;
    }
    
    public static class MyAnotherException extends Exception {
        private static final long serialVersionUID = 1L;
    }
    
    public static class MyExceptionProcessor implements Processor {        
        public void process(Exchange exchange) throws Exception {
            throw new MyException();            
        }        
    }
    
    public static class MyAnotherExceptionProcessor implements Processor {
        public void process(Exchange exchange) throws Exception {
            throw new MyAnotherException();            
        }
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:exception").loadBalance()
                    // catch all the exception here
                    .failover().to("direct:x", "direct:y", "direct:z");
                
                from("direct:customerException").loadBalance()
                    .failover(MyException.class).to("direct:x", "direct:y", "direct:z");
                
                from("direct:x").process(new MyExceptionProcessor()).to("mock:x");
                
                from("direct:y").process(new MyAnotherExceptionProcessor()).to("mock:y");
                
                from("direct:z").to("mock:z");
                
            }
        };
    }

    public void testThrowable() throws Exception {
        String body = "<one/>";
        expectsMessageCount(0, x, y);
        z.expectedBodiesReceived(body);
        sendMessage("direct:exception", "bar", body);
        assertMockEndpointsSatisfied();
    }
    
    public void testMyException() throws Exception {
        String body = "<two/>";
        expectsMessageCount(0, x, y, z);
        try {
            sendMessage("direct:customerException", "bar", body);
            fail("There should get the MyAnotherException");
        } catch (RuntimeCamelException ex) {
            // expect the exception here
            assertTrue("The cause should be MyAnotherException", ex.getCause() instanceof MyAnotherException);
        }
        assertMockEndpointsSatisfied();
    }

    protected void sendMessage(final String endpoint, final Object headerValue, final Object body) throws Exception {
        template.sendBodyAndHeader(endpoint, body, "foo", headerValue);
    }
}
