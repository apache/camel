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

import javax.naming.Context;

import org.apache.camel.Body;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.util.jndi.JndiContext;

public class ScatterGatherTest extends ContextTestSupport {

    public void testScatterAndGather() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.expectedBodiesReceived(1); // expect the lowest quote
        
        template.sendBodyAndHeader("direct:start", "<quote_request/>", "correlationId", "myid");
        
        result.assertIsSatisfied();
    }

    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext jndi = new JndiContext();
        jndi.bind("vendor1", new MyVendor(1));
        jndi.bind("vendor2", new MyVendor(2));
        jndi.bind("vendor3", new MyVendor(3));
        return jndi;
    }
    
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").multicast().to("seda:vendor1", "seda:vendor2", "seda:vendor3");
                
                from("seda:vendor1").to("bean:vendor1", "seda:aggregate");
                from("seda:vendor2").to("bean:vendor2", "seda:aggregate");
                from("seda:vendor3").to("bean:vendor3", "seda:aggregate");                
                
                from("seda:aggregate").aggregate(new LowestQuoteAggregationStrategy()).header("correlationId").to("mock:result");
            }
        };
    }
    
    public static class LowestQuoteAggregationStrategy implements AggregationStrategy {
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange.getIn().getBody(int.class) < newExchange.getIn().getBody(int.class)) {
                return oldExchange;
            } else {
                return newExchange;
            }
        }
    }
    
    public static class MyVendor {
        int quoteResult = 0;
        
        public MyVendor(int quoteResult) {
            this.quoteResult = quoteResult;
        }
        
        public void getQuote(Exchange exchange) {
            exchange.getIn().setBody(quoteResult);
        }
    }
}
