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
package org.apache.camel.component.jetty;

import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpOperationFailedException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Based on end user on forum how to get the 404 error code in his enrich aggregator
 *
 * @version $Revision$
 */
public class JettyHandleExceptionTest extends CamelTestSupport {

    public String getProducerUrl() {
        return "http://localhost:8123/myserver?user=Camel";
    }

    @Test
    public void testValidationException() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, HttpServletResponse.SC_BAD_REQUEST);
        
        template.sendBody("direct:start", "ValidationException");
        String response = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertMockEndpointsSatisfied();
        assertTrue("Should get the ValidationExcpetion", response.startsWith("org.apache.camel.ValidationException"));
    }
    
    @Test
    public void testExchangeTimedOutException() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, HttpServletResponse.SC_GATEWAY_TIMEOUT);
        
        template.sendBody("direct:start", "ExchangeTimedOutException");
        String response = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertMockEndpointsSatisfied();
        assertTrue("Should get the ExchangeTimedOutException", response.startsWith("org.apache.camel.ExchangeTimedOutException"));
    }
    
    @Test
    public void testOtherException() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        
        template.sendBody("direct:start", "OtherException");
        String response = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertMockEndpointsSatisfied();
        assertTrue("Should get the RuntimeCamelException", response.startsWith("org.apache.camel.RuntimeCamelException"));
    }
    
    @Test
    public void testHttpRouteProcessorException() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        
        template.sendBody("direct:router", "otherException");
        String response = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertMockEndpointsSatisfied();
        assertTrue("Should get the RuntimeCamelException", response.startsWith("org.apache.camel.RuntimeCamelException"));
        
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(ValidationException.class).setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpServletResponse.SC_BAD_REQUEST));
                onException(ExchangeTimedOutException.class).setOutHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpServletResponse.SC_GATEWAY_TIMEOUT));
                onException(RuntimeCamelException.class).setOutHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
                
                from("direct:start").to("jetty://http://localhost:8123/myserver?throwExceptionOnFailure=false").to("mock:result");
                // this is our jetty server where we simulate the 404
                from("jetty://http://localhost:8123/myserver")
                        
                            .process(new Processor() {
                                public void process(Exchange exchange) throws Exception {
                                    String request = exchange.getIn().getBody(String.class);
                                    if (request.equals("ValidationException")) {
                                        throw new ValidationException(exchange, request);
                                    } 
                                    if (request.equals("ExchangeTimedOutException")) {
                                        throw new ExchangeTimedOutException(exchange, 200);
                                    }
                                    throw new RuntimeCamelException("Runtime exception");
                                }
                            });
                
                from("direct:router").to("http://localhost:8125/router?throwExceptionOnFailure=false").to("mock:result");
                
                from("jetty://http://localhost:8124/server").process(new Processor() {

                    public void process(Exchange exchange) throws Exception {
                        // set the out message directly
                        exchange.getOut().setBody(exchange.getIn().getBody());
                    }
                    
                });
                from("jetty://http://localhost:8125/router")
                    .to("http://localhost:8124/server?bridgeEndpoint=true").process(new Processor() {

                        public void process(Exchange exchange) throws Exception {
                            String request = exchange.getIn().getBody(String.class);
                            if (request.equals("ValidationException")) {
                                throw new ValidationException(exchange, request);
                            }
                            throw new RuntimeCamelException("Runtime exception");
                        }
                        
                    });
                         
            }
            
        };
    }
    
}
