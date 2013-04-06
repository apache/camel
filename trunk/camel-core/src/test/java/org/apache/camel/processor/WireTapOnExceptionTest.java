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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;

/**
 *
 */
public class WireTapOnExceptionTest extends ContextTestSupport {
    
    public void testWireTapOnException() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                MyProducerFailEndpoint my = new MyProducerFailEndpoint("myEndpoint://foo", getContext());
                context.addEndpoint("myEndpoint://foo", my);

                onException(IllegalArgumentException.class).to("mock:error");
                
                from("direct:start").wireTap("myEndpoint:foo").to("mock:result");
            }
        };
    }

    public static final class MyProducerFailEndpoint extends DefaultEndpoint {
        private MyProducerFailEndpoint(String endpointUri, CamelContext camelContext) {
            super(endpointUri, (Component)null);
            setCamelContext(camelContext);
        }

        @Override
        public Producer createProducer() throws Exception {
            return new DefaultProducer(this) {
                @Override
                public void process(Exchange exchange) throws Exception {
                    throw new IllegalArgumentException("Forced");
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return null;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }
}
