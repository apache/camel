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
package org.apache.camel.builder;

import java.util.ArrayList;
import java.util.Collections;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.TestSupport;
import org.apache.camel.ValidationException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.DelegateProcessor;

/**
 * @version $Revision$
 */
public class BuilderWithScopesTest extends TestSupport {

    final ArrayList<String> order = new ArrayList<String>();
    final DelegateProcessor interceptor1 = new DelegateProcessor() {
        @Override
        public void process(Exchange exchange) throws Exception {
            order.add("START:1");
            super.process(exchange);
            order.add("END:1");
        }
    };
    final DelegateProcessor interceptor2 = new DelegateProcessor() {
        @Override
        public void process(Exchange exchange) throws Exception {
            order.add("START:2");
            super.process(exchange);
            order.add("END:2");
        }
    };
    final Processor orderProcessor = new Processor() {
        public void process(Exchange exchange) {
            order.add("INVOKED");
        }
    };
    final Processor orderProcessor2 = new Processor() {
        public void process(Exchange exchange) {
            order.add("INVOKED2");
        }
    };
    final Processor orderProcessor3 = new Processor() {
        public void process(Exchange exchange) {
            order.add("INVOKED3");
        }
    };
    final Processor toProcessor = new Processor() {
        public void process(Exchange exchange) {
            order.add("TO");
        }
    };
    final Processor validator = new Processor() {
        public void process(Exchange exchange) throws Exception {
            order.add("VALIDATE");
            Object value = exchange.getIn().getHeader("foo");
            if (value == null) {
                throw new IllegalArgumentException("The foo header is not present.");
            } else if (!value.equals("bar")) {
                throw new ValidationException(exchange, "The foo header does not equal bar! Was: " + value);
            }
        }
    };

    protected void runTest(RouteBuilder builder, ArrayList<String> expected) throws Exception {
        runTest(builder, expected, null);
    }

    protected void runTest(RouteBuilder builder, ArrayList<String> expected, String header) throws Exception {

        order.clear();
        CamelContext container = new DefaultCamelContext();

        container.addRoutes(builder);
        container.start();

        Endpoint endpoint = container.getEndpoint("direct:a");
        Exchange exchange = endpoint.createExchange();
        if (header != null) {
            exchange.getIn().setHeader("foo", header);
        }
        Producer producer = endpoint.createProducer();
        producer.process(exchange);

        log.debug("Interceptor invocation order:" + order);
        assertEquals(expected, order);
    }

    public void testRouteWithFilterEnd() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("TO");

        runTest(new RouteBuilder() {
            public void configure() {
                from("direct:a").filter(header("foo").isEqualTo("bar")).process(orderProcessor).end()
                    .process(toProcessor);
            }
        }, expected, "banana");
    }

    public void testRouteWithFilterNoEnd() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();

        runTest(new RouteBuilder() {
            public void configure() {
                from("direct:a").filter(header("foo").isEqualTo("bar")).process(orderProcessor)
                    .process(toProcessor);
            }
        }, expected, "banana");
    }

    protected RouteBuilder createChoiceBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").choice().when(header("foo").isEqualTo("bar")).process(orderProcessor)
                    .when(header("foo").isEqualTo("cheese")).process(orderProcessor2).end()
                    .process(toProcessor);
            }
        };
    }

    public void testRouteWithChoice1() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("INVOKED");
        expected.add("TO");

        runTest(createChoiceBuilder(), expected, "bar");
    }

    public void testRouteWithChoice2() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("INVOKED2");
        expected.add("TO");

        runTest(createChoiceBuilder(), expected, "cheese");
    }

    public void testRouteWithChoice3() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("TO");

        runTest(createChoiceBuilder(), expected, "banana");
    }

    public void testRouteWithChoiceNoEnd() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("INVOKED");

        runTest(new RouteBuilder() {
            public void configure() {
                from("direct:a").choice().when(header("foo").isEqualTo("bar")).process(orderProcessor)
                    .when(header("foo").isEqualTo("cheese")).process(orderProcessor2).process(toProcessor); // continuation of the second when clause
            }
        }, expected, "bar");
    }

    protected RouteBuilder createChoiceWithOtherwiseBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").choice().when(header("foo").isEqualTo("bar")).process(orderProcessor)
                    .when(header("foo").isEqualTo("cheese")).process(orderProcessor2).otherwise()
                    .process(orderProcessor3).end().process(toProcessor);
            }
        };
    }

    public void testRouteWithChoiceOtherwise1() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("INVOKED");
        expected.add("TO");

        runTest(createChoiceWithOtherwiseBuilder(), expected, "bar");
    }

    public void testRouteWithChoiceOtherwise2() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("INVOKED2");
        expected.add("TO");

        runTest(createChoiceWithOtherwiseBuilder(), expected, "cheese");
    }

    public void testRouteWithChoiceOtherwise3() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("INVOKED3");
        expected.add("TO");
        runTest(createChoiceWithOtherwiseBuilder(), expected, "banana");
    }

    public void testRouteWithChoiceOtherwiseNoEnd() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("INVOKED");

        runTest(new RouteBuilder() {
            public void configure() {
                from("direct:a").choice().when(header("foo").isEqualTo("bar")).process(orderProcessor)
                    .when(header("foo").isEqualTo("cheese")).process(orderProcessor2).otherwise()
                    .process(orderProcessor3).process(toProcessor); // continuation of the otherwise clause
            }
        }, expected, "bar");
    }

    protected RouteBuilder createTryCatchNoEnd() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").tryBlock().process(validator).process(toProcessor)
                    .handle(ValidationException.class).process(orderProcessor).process(orderProcessor3); // continuation of the handle clause
            }
        };
    }

    public void testRouteWithTryCatchNoEndNoException() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("VALIDATE");
        expected.add("TO");

        runTest(createTryCatchNoEnd(), expected, "bar");
    }

    public void testRouteWithTryCatchNoEndWithCaughtException() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("VALIDATE");
        expected.add("INVOKED");
        expected.add("INVOKED3");

        runTest(createTryCatchNoEnd(), expected, "banana");
    }

    public void testRouteWithTryCatchNoEndWithUncaughtException() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.addAll(Collections.nCopies(6, "VALIDATE"));

        runTest(createTryCatchNoEnd(), expected);
    }

    protected RouteBuilder createTryCatchEnd() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").tryBlock().process(validator).process(toProcessor)
                    .handle(ValidationException.class).process(orderProcessor).end().process(orderProcessor3);
            }
        };
    }

    public void testRouteWithTryCatchEndNoException() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("VALIDATE");
        expected.add("TO");
        expected.add("INVOKED3");

        runTest(createTryCatchEnd(), expected, "bar");
    }

    public void testRouteWithTryCatchEndWithCaughtException() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("VALIDATE");
        expected.add("INVOKED");
        expected.add("INVOKED3");

        runTest(createTryCatchEnd(), expected, "banana");
    }

    public void testRouteWithTryCatchEndWithUncaughtException() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.addAll(Collections.nCopies(6, "VALIDATE"));

        runTest(createTryCatchEnd(), expected);
    }

    protected RouteBuilder createTryCatchFinallyNoEnd() {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel().maximumRedeliveries(2));
                from("direct:a").tryBlock().process(validator).process(toProcessor)
                    .handle(ValidationException.class).process(orderProcessor).finallyBlock()
                    .process(orderProcessor2).process(orderProcessor3); // continuation of the finallyBlock clause
            }
        };
    }

    public void testRouteWithTryCatchFinallyNoEndNoException() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("VALIDATE");
        expected.add("TO");
        expected.add("INVOKED2");
        expected.add("INVOKED3");

        runTest(createTryCatchFinallyNoEnd(), expected, "bar");
    }

    public void testRouteWithTryCatchFinallyNoEndWithCaughtException() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("VALIDATE");
        expected.add("INVOKED");
        expected.add("INVOKED2");
        expected.add("INVOKED3");

        runTest(createTryCatchFinallyNoEnd(), expected, "banana");
    }

    public void testRouteWithTryCatchFinallyNoEndWithUncaughtException() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("VALIDATE");
        expected.add("INVOKED2");
        expected.add("INVOKED3");
        // exchange should be processed twice for an uncaught exception and maximumRedeliveries(2)
        expected.add("VALIDATE");
        expected.add("INVOKED2");
        expected.add("INVOKED3");

        runTest(createTryCatchFinallyNoEnd(), expected);
    }

    protected RouteBuilder createTryCatchFinallyEnd() {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel().maximumRedeliveries(2));
                from("direct:a").tryBlock().process(validator).process(toProcessor)
                    .handle(ValidationException.class).process(orderProcessor).finallyBlock()
                    .process(orderProcessor2).end().process(orderProcessor3);
            }
        };
    };

    public void testRouteWithTryCatchFinallyEndNoException() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("VALIDATE");
        expected.add("TO");
        expected.add("INVOKED2");
        expected.add("INVOKED3");

        runTest(createTryCatchFinallyEnd(), expected, "bar");
    }

    public void testRouteWithTryCatchFinallyEndWithCaughtException() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("VALIDATE");
        expected.add("INVOKED");
        expected.add("INVOKED2");
        expected.add("INVOKED3");

        runTest(createTryCatchFinallyEnd(), expected, "banana");
    }

    public void testRouteWithTryCatchFinallyEndWithUncaughtException() throws Exception {
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("VALIDATE");
        expected.add("INVOKED2");
        // exchange should be processed twice for an uncaught exception and maximumRedeliveries(2)
        expected.add("VALIDATE");
        expected.add("INVOKED2");
        // orderProcessor3 will not be invoked past end() with an uncaught exception

        runTest(createTryCatchFinallyEnd(), expected);
    }
}
