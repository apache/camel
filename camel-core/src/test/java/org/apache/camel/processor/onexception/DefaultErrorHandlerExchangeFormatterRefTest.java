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
import org.apache.camel.spi.ExchangeFormatter;

public class DefaultErrorHandlerExchangeFormatterRefTest extends ContextTestSupport {

    private static int invoked;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myExchangeFormatter", new MyExchangeFormatter());
        return jndi;
    }

    public void testRetryUntil() throws Exception {
        try {
            template.requestBody("direct:start", "Hello World");
            fail("Expected the exception");
        } catch (Exception ex) {
            // expect the exception here; 
        }
        assertEquals(1, invoked);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(defaultErrorHandler().exchangeFormatterRef("myExchangeFormatter"));

                from("direct:start").process(new MyProcessor());
            }
        };
    }

    public static class MyProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            throw new MyFunctionalException("Sorry you cannot do this");
        }
    }

    public static class MyExchangeFormatter implements ExchangeFormatter {

        @Override
        public String format(Exchange exchange) {
            invoked++;
            return "Exchange";
        }
    }

}