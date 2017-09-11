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
package org.apache.camel.processor.onexception;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;

/**
 * Unit test for the retry until predicate
 */
public class DefaultErrorHandlerRetryWhileTest extends ContextTestSupport {

    private static int invoked;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myRetryHandler", new MyRetryBean());
        return jndi;
    }

    public void testRetryUntil() throws Exception {
        Object out = template.requestBody("direct:start", "Hello World");
        assertEquals("Bye World", out);
        assertEquals(3, invoked);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(defaultErrorHandler().redeliveryDelay(0).retryWhile(method("myRetryHandler")));

                from("direct:start").process(new MyProcessor());
            }
        };
    }

    public static class MyProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            if (invoked < 3) {
                throw new MyFunctionalException("Sorry you cannot do this");
            }
            exchange.getIn().setBody("Bye World");
        }
    }

    public static class MyRetryBean {

        public boolean retry() {
            // force retry forever
            invoked++;
            return true;
        }
    }

}