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

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RouteNode;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.management.InstrumentationProcessor;
import org.apache.camel.spi.TracedRouteNodes;

/**
 * @version 
 */
public class TraceableUnitOfWorkTest extends ContextTestSupport {

    public void testSendingSomeMessages() throws Exception {
        Object out = template.requestBody("direct:start", "Hello London");
        assertEquals("Failed at: sendTo(bean://bar)", out);
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("foo", new MyFooBean());
        jndi.bind("bar", new MyBarBean());
        return jndi;
    }

    // START SNIPPET: e1
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(3));

                // must enable tracer to trace the route path taken during runtime
                context.setTracing(true);

                // let our my error processor handle all exceptions
                onException(Exception.class).handled(true).process(new MyErrorProcessor());

                // our route where an exception can be thrown from either foo or bar bean
                // so we have enable tracing so we can check it at runtime to get the actual
                // node path taken
                from("direct:start").to("bean:foo").to("bean:bar");
            }
        };
    }
    // END SNIPPET: e1

    // START SNIPPET: e2
    private static class MyErrorProcessor implements Processor {
        public void process(Exchange exchange) throws Exception {
            TracedRouteNodes traced = exchange.getUnitOfWork().getTracedRouteNodes();

            // get the list of intercepted nodes
            List<RouteNode> list = traced.getNodes();
            // get the 3rd last as its the bean
            Processor last = list.get(list.size() - 3).getProcessor();

            // wrapped by JMX
            if (last instanceof InstrumentationProcessor) {
                InstrumentationProcessor ip = (InstrumentationProcessor) last;
                last = ip.getProcessor();
            }

            // set error message
            exchange.getOut().setFault(true);
            exchange.getOut().setBody("Failed at: " + last.toString());
        }

        public String toString() {
            return "MyErrorProcessor";
        }
    }
    // END SNIPPET: e2

    public class MyFooBean {
        public void process(Exchange exchange) throws Exception {
            exchange.getOut().setBody("Foo okay");
        }
    }

    public class MyBarBean {
        public void process(Exchange exchange) throws Exception {
            throw new IllegalArgumentException("Damm Bar");
        }
    }
}