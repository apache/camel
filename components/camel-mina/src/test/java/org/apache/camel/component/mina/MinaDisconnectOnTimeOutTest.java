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
package org.apache.camel.component.mina;

import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.UnrecoverableExceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * To test timeout and disconnect
 */
public class MinaDisconnectOnTimeOutTest extends BaseMinaTest {

    private static final String ENDPOINT = "mina:tcp://localhost:%s?textline=true&sync=true&timeout=250&disconnectOnNoReply=%s";
    private static final String RESPONSE = "Slept %s ms";

    @Test
    public void testBothRequestsTimeout() {
        int tooLong = 400;
        assertThrowable(ExchangeTimedOutException.class, () -> requestWithDelay(tooLong, true));
        assertThrowable(ExchangeTimedOutException.class, () -> requestWithDelay(tooLong, true));
    }

    @Test
    public void testSecondRequestReturnsCorrectResult() {
        int tooLong = 350;
        int fastEnough = 150;
        assertThrowable(ExchangeTimedOutException.class, () -> requestWithDelay(tooLong, true));
        assertEquals(response(fastEnough), requestWithDelay(fastEnough, true));
    }

    @Test
    public void testSecondRequestReturnsWrongResult() {
        int tooLong = 350;
        int fastEnough = 150;
        assertThrowable(ExchangeTimedOutException.class, () -> requestWithDelay(tooLong, false));
        // YIKES! The response from the first query is returned for the second query. Better leave disconnectOnNoReply on true!
        assertEquals(response(tooLong), requestWithDelay(fastEnough, false));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                fromF("mina:tcp://localhost:%1$s?textline=true&sync=true&timeout=30000", getPort())
                        .process(e -> {
                            int sleep = e.getMessage().getBody(Integer.class);
                            Thread.sleep(sleep);
                            e.getMessage().setBody(response(sleep));
                        });
            }
        };
    }

    private String requestWithDelay(int sleep, boolean disconnect) {
        return template.requestBody(String.format(ENDPOINT, getPort(), disconnect), sleep, String.class);
    }

    private static String response(int sleep) {
        return String.format(RESPONSE, sleep);
    }

    private static void assertThrowable(Class<? extends Throwable> expectedCause, Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            Throwable t2 = t;
            while (t2.getCause() != null) {
                t2 = t2.getCause();
                if (expectedCause.isInstance(t2)) {
                    return;
                }
            }
            UnrecoverableExceptions.rethrowIfUnrecoverable(t);
            throw new AssertionError(
                    String.format("Expected exception %s, but exception stack did not contain it", expectedCause), t);
        }
        throw new AssertionError(String.format("Expected %s to be thrown, but nothing was thrown", expectedCause));
    }
}
