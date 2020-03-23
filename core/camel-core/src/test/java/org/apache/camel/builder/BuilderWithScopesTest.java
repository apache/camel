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
package org.apache.camel.builder;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.TestSupport;
import org.apache.camel.ValidationException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.processor.DelegateProcessor;
import org.junit.Test;

public class BuilderWithScopesTest extends TestSupport {

    final List<String> order = new ArrayList<>();
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

    protected void runTest(RouteBuilder builder, List<String> expected) throws Exception {
        runTest(builder, expected, null);
    }

    protected void runTest(RouteBuilder builder, List<String> expected, String header) throws Exception {

        order.clear();
        DefaultCamelContext container = new DefaultCamelContext(false);
        container.disableJMX();
        container.build();

        container.addRoutes(builder);
        container.start();

        Endpoint endpoint = container.getEndpoint("direct:a");
        Exchange exchange = endpoint.createExchange();
        if (header != null) {
            exchange.getIn().setHeader("foo", header);
        }
        Producer producer = endpoint.createProducer();
        producer.process(exchange);

        log.debug("Invocation order:" + order);
        assertEquals(expected, order);

        container.stop();
    }

    @Test
    public void testRouteWithFilterEnd() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("TO");

        runTest(new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(3));

                from("direct:a").filter(header("foo").isEqualTo("bar")).process(orderProcessor).end().process(toProcessor);
            }
        }, expected, "banana");
    }

    @Test
    public void testRouteWithFilterNoEnd() throws Exception {
        List<String> expected = new ArrayList<>();

        runTest(new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(3));

                from("direct:a").filter(header("foo").isEqualTo("bar")).process(orderProcessor).process(toProcessor);
            }
        }, expected, "banana");
    }

    protected RouteBuilder createChoiceBuilder() {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(3));

                from("direct:a").choice().when(header("foo").isEqualTo("bar")).process(orderProcessor).when(header("foo").isEqualTo("cheese")).process(orderProcessor2).end()
                    .process(toProcessor);
            }
        };
    }

    @Test
    public void testRouteWithChoice1() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("INVOKED");
        expected.add("TO");

        runTest(createChoiceBuilder(), expected, "bar");
    }

    @Test
    public void testRouteWithChoice2() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("INVOKED2");
        expected.add("TO");

        runTest(createChoiceBuilder(), expected, "cheese");
    }

    @Test
    public void testRouteWithChoice3() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("TO");

        runTest(createChoiceBuilder(), expected, "banana");
    }

    @Test
    public void testRouteWithChoiceNoEnd() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("INVOKED");

        runTest(new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(3));

                from("direct:a").choice().when(header("foo").isEqualTo("bar")).process(orderProcessor).when(header("foo").isEqualTo("cheese")).process(orderProcessor2)
                    .process(toProcessor); // continuation of the second when
                                           // clause
            }
        }, expected, "bar");
    }

    protected RouteBuilder createChoiceWithOtherwiseBuilder() {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(3));

                from("direct:a").choice().when(header("foo").isEqualTo("bar")).process(orderProcessor).when(header("foo").isEqualTo("cheese")).process(orderProcessor2).otherwise()
                    .process(orderProcessor3).end().process(toProcessor);
            }
        };
    }

    @Test
    public void testRouteWithChoiceOtherwise1() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("INVOKED");
        expected.add("TO");

        runTest(createChoiceWithOtherwiseBuilder(), expected, "bar");
    }

    @Test
    public void testRouteWithChoiceOtherwise2() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("INVOKED2");
        expected.add("TO");

        runTest(createChoiceWithOtherwiseBuilder(), expected, "cheese");
    }

    @Test
    public void testRouteWithChoiceOtherwise3() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("INVOKED3");
        expected.add("TO");
        runTest(createChoiceWithOtherwiseBuilder(), expected, "banana");
    }

    @Test
    public void testRouteWithChoiceOtherwiseNoEnd() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("INVOKED");

        runTest(new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(3));

                from("direct:a").choice().when(header("foo").isEqualTo("bar")).process(orderProcessor).when(header("foo").isEqualTo("cheese")).process(orderProcessor2).otherwise()
                    .process(orderProcessor3).process(toProcessor); // continuation
                                                                    // of the
                                                                    // otherwise
                                                                    // clause
            }
        }, expected, "bar");
    }

    protected RouteBuilder createTryCatchNoEnd() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").doTry().process(validator).process(toProcessor).doCatch(ValidationException.class).process(orderProcessor).process(orderProcessor3).end();
            }
        };
    }

    @Test
    public void testRouteWithTryCatchNoEndNoException() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("VALIDATE");
        expected.add("TO");

        runTest(createTryCatchNoEnd(), expected, "bar");
    }

    @Test
    public void testRouteWithTryCatchNoEndWithCaughtException() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("VALIDATE");
        expected.add("INVOKED");
        expected.add("INVOKED3");

        runTest(createTryCatchNoEnd(), expected, "banana");
    }

    @Test
    public void testRouteWithTryCatchNoEndWithUncaughtException() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("VALIDATE");

        runTest(createTryCatchNoEnd(), expected);
    }

    protected RouteBuilder createTryCatchEnd() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").doTry().process(validator).process(toProcessor).doCatch(ValidationException.class).process(orderProcessor).end().process(orderProcessor3);
            }
        };
    }

    @Test
    public void testRouteWithTryCatchEndNoException() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("VALIDATE");
        expected.add("TO");
        expected.add("INVOKED3");

        runTest(createTryCatchEnd(), expected, "bar");
    }

    @Test
    public void testRouteWithTryCatchEndWithCaughtException() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("VALIDATE");
        expected.add("INVOKED");
        expected.add("INVOKED3");

        runTest(createTryCatchEnd(), expected, "banana");
    }

    @Test
    public void testRouteWithTryCatchEndWithUncaughtException() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("VALIDATE");

        runTest(createTryCatchEnd(), expected);
    }

    protected RouteBuilder createTryCatchFinallyNoEnd() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").doTry().process(validator).process(toProcessor).doCatch(ValidationException.class).process(orderProcessor).doFinally().process(orderProcessor2)
                    .process(orderProcessor3); // continuation of the
                                               // finallyBlock clause
            }
        };
    }

    @Test
    public void testRouteWithTryCatchFinallyNoEndNoException() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("VALIDATE");
        expected.add("TO");
        expected.add("INVOKED2");
        expected.add("INVOKED3");

        runTest(createTryCatchFinallyNoEnd(), expected, "bar");
    }

    @Test
    public void testRouteWithTryCatchFinallyNoEndWithCaughtException() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("VALIDATE");
        expected.add("INVOKED");
        expected.add("INVOKED2");
        expected.add("INVOKED3");

        runTest(createTryCatchFinallyNoEnd(), expected, "banana");
    }

    @Test
    public void testRouteWithTryCatchFinallyNoEndWithUncaughtException() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("VALIDATE");
        expected.add("INVOKED2");
        expected.add("INVOKED3");

        runTest(createTryCatchFinallyNoEnd(), expected);
    }

    protected RouteBuilder createTryCatchFinallyEnd() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").doTry().process(validator).process(toProcessor).doCatch(ValidationException.class).process(orderProcessor).doFinally().process(orderProcessor2)
                    .end().process(orderProcessor3);
            }
        };
    }

    @Test
    public void testRouteWithTryCatchFinallyEndNoException() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("VALIDATE");
        expected.add("TO");
        expected.add("INVOKED2");
        expected.add("INVOKED3");

        runTest(createTryCatchFinallyEnd(), expected, "bar");
    }

    @Test
    public void testRouteWithTryCatchFinallyEndWithCaughtException() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("VALIDATE");
        expected.add("INVOKED");
        expected.add("INVOKED2");
        expected.add("INVOKED3");

        runTest(createTryCatchFinallyEnd(), expected, "banana");
    }

    @Test
    public void testRouteWithTryCatchFinallyEndWithUncaughtException() throws Exception {
        List<String> expected = new ArrayList<>();
        expected.add("VALIDATE");
        expected.add("INVOKED2");

        runTest(createTryCatchFinallyEnd(), expected);
    }
}
