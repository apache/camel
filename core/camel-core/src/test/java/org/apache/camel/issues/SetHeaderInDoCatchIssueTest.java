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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultRegistry;
import org.junit.Test;

public class SetHeaderInDoCatchIssueTest extends ContextTestSupport {

    @Test
    public void testSuccess() {
        Exchange exchange = template.request("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                // empty message
            }
        });

        assertEquals("CamsResponse", exchange.getOut().getHeader("Status"));
    }

    @Test
    public void testExchangeTimedOutException() {
        Exchange exchange = template.request("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("ExchangeTimedOutException");
            }
        });

        assertEquals("TimeOut", exchange.getMessage().getHeader("Status"));
    }

    @Test
    public void testException() {
        Exchange exchange = template.request("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Exception");
            }
        });

        assertEquals("ExceptionGeneral", exchange.getMessage().getHeader("Status"));
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry registry = new DefaultRegistry();

        registry.bind("A", new Processor() {
            public void process(Exchange exchange) throws Exception {
                log.info("A headers " + exchange.getIn().getHeaders());
            }
        });

        registry.bind("B", new Processor() {
            public void process(Exchange exchange) throws Exception {
                log.info("B headers " + exchange.getIn().getHeaders());

                if ("ExchangeTimedOutException".equals(exchange.getIn().getBody(String.class))) {
                    throw new ExchangeTimedOutException(exchange, 1);
                } else if ("Exception".equals(exchange.getIn().getBody(String.class))) {
                    throw new Exception();
                }
            }
        });

        registry.bind("C", new Processor() {
            public void process(Exchange exchange) throws Exception {
                log.info("C headers " + exchange.getIn().getHeaders());
            }
        });

        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setTracing(true);

                from("direct:start").doTry().to("bean:A").setHeader("CamelJmsDestinationName", constant("queue:outQueue")).inOut("bean:B").setHeader("Status", constant("CamsResponse"))
                    .doCatch(ExchangeTimedOutException.class).setHeader("Status", constant("TimeOut")).doCatch(Exception.class).setHeader("Status", constant("ExceptionGeneral"))
                    .end().to("bean:C").transform(body());
            }
        };
    }

}
