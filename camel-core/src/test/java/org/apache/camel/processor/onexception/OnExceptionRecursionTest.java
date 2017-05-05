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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Assert;



/**
 * Test that exceptions in an onException handler route do not go into recursion
 */
public class OnExceptionRecursionTest extends ContextTestSupport {
    private int counter;

    public void testRecursion() throws Exception {
        try {
            template.sendBody("direct:start", "Hello World");
        } catch (CamelExecutionException e) {
            Throwable inner = e.getCause();
            Assert.assertEquals("Simulate exception in route", inner.getMessage());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(MyException.class).process(new Processor() {
                    
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        System.out.println();
                    }
                }).to("direct:exhandler");
                from("direct:exhandler").process(recursionProcessor())
                    .throwException(new MyException("Simulate Exception in handler route"));
                from("direct:start").throwException(new MyException("Simulate exception in route"));
            }

        };

    }

    private Processor recursionProcessor() {
        return new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                counter++;
                if (counter > 1) {
                    throw new IllegalStateException("Test failed as camel would go into recursion");
                }

            }
        };
    }

    class MyException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        MyException(String msg) {
            super(msg);
        }
    };

}
