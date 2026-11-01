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
package org.apache.camel.processor.throttle;

import java.io.IOException;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.throttling.ThrottlingExceptionRoutePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The exceptions of {@link ThrottlingExceptionRoutePolicy} are matched against the exception the exchange fails with
 * and its causes, as onException and doCatch do, and not against its suppressed exceptions. Each route has its own
 * policy that opens its circuit on the first failure it counts.
 */
class ThrottlingExceptionRoutePolicySuppressedExceptionTest extends ContextTestSupport {

    private final ThrottlingExceptionRoutePolicy finallyPolicy = policy(IOException.class);
    private final ThrottlingExceptionRoutePolicy onExceptionPolicy = policy(IOException.class);
    private final ThrottlingExceptionRoutePolicy redeliveryPolicy = policy(IOException.class);
    private final ThrottlingExceptionRoutePolicy addedPolicy = policy(IOException.class);
    private final ThrottlingExceptionRoutePolicy directPolicy = policy(IOException.class);
    private final ThrottlingExceptionRoutePolicy causePolicy = policy(IOException.class);
    private final ThrottlingExceptionRoutePolicy listedInTryPolicy = policy(IOException.class);
    private final ThrottlingExceptionRoutePolicy allPolicy = policy();

    private static ThrottlingExceptionRoutePolicy policy(Class<?>... exceptions) {
        // a threshold of 1: the first failure that counts opens the circuit
        return new ThrottlingExceptionRoutePolicy(1, 60000, 60000, List.of(exceptions));
    }

    private Exchange send(String uri) {
        return template.send(uri, e -> e.getMessage().setBody("hello"));
    }

    private static void assertSuppressed(Class<?> expected, Exception exception) {
        assertEquals(1, exception.getSuppressed().length, "suppressed exceptions of " + exception);
        assertInstanceOf(expected, exception.getSuppressed()[0]);
    }

    private static void assertNotCounted(ThrottlingExceptionRoutePolicy policy) {
        assertEquals(0, policy.getFailures());
        assertEquals(1, policy.getSuccess());
        assertEquals("closed", policy.getStateAsString());
    }

    private static void assertCounted(ThrottlingExceptionRoutePolicy policy) {
        assertEquals(1, policy.getFailures());
        assertEquals(0, policy.getSuccess());
        assertEquals("opened", policy.getStateAsString());
    }

    @Test
    void listedExceptionThrownInDoFinallyIsNotCounted() {
        Exchange out = send("direct:finally");

        assertInstanceOf(IllegalStateException.class, out.getException());
        assertSuppressed(IOException.class, out.getException());
        assertNotCounted(finallyPolicy);
    }

    @Test
    void listedExceptionHandledByAFailingOnExceptionIsNotCounted() {
        Exchange out = send("direct:onException");

        // the FatalFallbackErrorHandler fails the exchange with the exception of the onException block
        assertInstanceOf(IllegalStateException.class, out.getException());
        assertSuppressed(IOException.class, out.getException());
        assertNotCounted(onExceptionPolicy);
    }

    @Test
    void listedExceptionOfTheFirstAttemptIsNotCountedWhenTheRedeliveryFailsWithAnother() {
        Exchange out = send("direct:redelivery");

        assertInstanceOf(IllegalStateException.class, out.getException());
        assertSuppressed(IOException.class, out.getException());
        assertNotCounted(redeliveryPolicy);
    }

    @Test
    void listedExceptionAddedAsSuppressedByAProcessorIsNotCounted() {
        Exchange out = send("direct:added");

        assertInstanceOf(IllegalStateException.class, out.getException());
        assertSuppressed(IOException.class, out.getException());
        assertNotCounted(addedPolicy);
    }

    // control
    @Test
    void listedExceptionThrownDirectlyIsCounted() {
        Exchange out = send("direct:direct");

        assertInstanceOf(IOException.class, out.getException());
        assertCounted(directPolicy);
    }

    // control
    @Test
    void listedExceptionAsTheCauseIsCounted() {
        Exchange out = send("direct:cause");

        assertInstanceOf(IllegalStateException.class, out.getException());
        assertInstanceOf(IOException.class, out.getException().getCause());
        assertCounted(causePolicy);
    }

    // control: the exception of the doTry block decides, also when the doFinally block throws an exception not listed
    @Test
    void listedExceptionThrownInDoTryIsCountedWhenDoFinallyThrowsAnother() {
        Exchange out = send("direct:listedInTry");

        assertInstanceOf(IOException.class, out.getException());
        assertSuppressed(IllegalStateException.class, out.getException());
        assertCounted(listedInTryPolicy);
    }

    // control: without exceptions to throttle on every failure counts
    @Test
    void everyFailureIsCountedWithoutExceptions() {
        Exchange out = send("direct:all");

        assertInstanceOf(IllegalStateException.class, out.getException());
        assertSuppressed(IOException.class, out.getException());
        assertCounted(allPolicy);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:finally").routePolicy(finallyPolicy)
                        .doTry()
                            .throwException(new IllegalStateException("try"))
                        .doFinally()
                            .throwException(new IOException("finally"))
                        .end();

                from("direct:onException").routePolicy(onExceptionPolicy)
                        .onException(IOException.class).handled(true)
                            .throwException(new IllegalStateException("onException"))
                        .end()
                        .throwException(new IOException("route"));

                from("direct:redelivery").routePolicy(redeliveryPolicy)
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(0))
                        .process(e -> {
                            if (e.getIn().getHeader(Exchange.REDELIVERY_COUNTER) == null) {
                                throw new IOException("first attempt");
                            }
                            throw new IllegalStateException("redelivery");
                        });

                from("direct:added").routePolicy(addedPolicy)
                        .process(e -> {
                            // as try-with-resources does when closing a resource fails
                            IllegalStateException failure = new IllegalStateException("process");
                            failure.addSuppressed(new IOException("close"));
                            throw failure;
                        });

                from("direct:direct").routePolicy(directPolicy)
                        .throwException(new IOException("direct"));

                from("direct:cause").routePolicy(causePolicy)
                        .throwException(new IllegalStateException("wrapper", new IOException("cause")));

                from("direct:listedInTry").routePolicy(listedInTryPolicy)
                        .doTry()
                            .throwException(new IOException("try"))
                        .doFinally()
                            .throwException(new IllegalStateException("finally"))
                        .end();

                from("direct:all").routePolicy(allPolicy)
                        .doTry()
                            .throwException(new IllegalStateException("try"))
                        .doFinally()
                            .throwException(new IOException("finally"))
                        .end();
            }
        };
    }
}
