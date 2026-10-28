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
package org.apache.camel.support;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.StepHook;
import org.apache.camel.util.TimeSource;

/**
 * Helper for the places in Camel that call the processor of a step of a route: the error handler of the step, and the
 * {@link org.apache.camel.Channel} of a step that has no error handler that does it. It asks the {@link StepHook} of
 * the {@link CamelContext}, if one is installed, before it calls the processor.
 *
 * @since 4.24
 */
public final class StepHelper {

    // returned by beforeStep when the hook failed the step
    private static final long FAILED = -1;

    private StepHelper() {
    }

    /**
     * Calls the processor of a step with the {@link StepHook} asked first. Without a hook this is
     * {@code processor.process(exchange, callback)}.
     * <p/>
     * With a hook, see {@link StepHook} for what the hook can do. A step that the hook failed completes right away
     * without calling the processor. For a delayed step this thread waits, and then calls the processor. An exception
     * the processor throws, instead of calling the callback, is thrown to the caller, as without a hook.
     *
     * @param  exchange  the exchange
     * @param  nodeId    the id of the step
     * @param  processor the processor of the step
     * @param  callback  the callback, called exactly once when the step is done
     * @return           whether the step completed synchronously (the same value the callback was called with)
     */
    public static boolean process(Exchange exchange, String nodeId, AsyncProcessor processor, AsyncCallback callback) {
        final StepHook hook = exchange.getContext().getCamelContextExtension().getStepHook();
        if (hook == null) {
            return processor.process(exchange, callback);
        }

        final long delay = beforeStep(hook, exchange, nodeId);
        if (delay == FAILED || delay > 0 && !sleep(exchange, delay)) {
            // the hook failed the step, or the wait was interrupted or failed, so the processor is not called
            callback.done(true);
            return true;
        }
        return processor.process(exchange, callback);
    }

    /**
     * Calls the processor of a step synchronously, with the {@link StepHook} asked first. Without a hook this is
     * {@code processor.process(exchange)}.
     * <p/>
     * A step that the hook failed returns without calling the processor, with the exception on the exchange. A delay
     * waits on this thread.
     *
     * @param  exchange  the exchange
     * @param  nodeId    the id of the step
     * @param  processor the processor of the step
     * @throws Exception the exception the processor throws
     */
    public static void process(Exchange exchange, String nodeId, Processor processor) throws Exception {
        final StepHook hook = exchange.getContext().getCamelContextExtension().getStepHook();
        if (hook != null) {
            final long delay = beforeStep(hook, exchange, nodeId);
            if (delay == FAILED || delay > 0 && !sleep(exchange, delay)) {
                // the hook failed the step, or the wait was interrupted or failed, so the processor is not called
                return;
            }
        }
        processor.process(exchange);
    }

    /**
     * Asks the hook.
     *
     * @return the delay in millis, 0 for none, or {@code FAILED} when the step failed (the exception is on the
     *         exchange), also when the hook returned a reserved negative value
     */
    private static long beforeStep(StepHook hook, Exchange exchange, String nodeId) {
        // an exchange can reach a step with an exception on it already, which does not fail the step
        final Exception existing = exchange.getException();
        final long delay;
        try {
            delay = hook.beforeStep(exchange, nodeId);
        } catch (Exception e) {
            exchange.setException(e);
            return FAILED;
        }
        final Exception exception = exchange.getException();
        if (exception != null && exception != existing) {
            return FAILED;
        }
        if (delay < 0) {
            // negative values are reserved, so a hook that returns one fails the step
            exchange.setException(new IllegalArgumentException(
                    "StepHook.beforeStep returned " + delay + ": negative values are reserved"));
            return FAILED;
        }
        return delay;
    }

    /**
     * Waits for the delay on this thread, as for a slow step (through TimeSource, so a virtual clock can run it).
     *
     * @return false when the wait was interrupted or failed (the exception is on the exchange)
     */
    private static boolean sleep(Exchange exchange, long delay) {
        try {
            TimeSource.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            // the step fails and the thread keeps its interrupted status; the exception marks the exchange as
            // interrupted, so Camel stops routing it, as when the wait of the redelivery error handler is interrupted
            Thread.currentThread().interrupt();
            exchange.setException(e);
            return false;
        } catch (RuntimeException e) {
            // the clock failed: a failure of the step
            exchange.setException(e);
            return false;
        }
    }
}
