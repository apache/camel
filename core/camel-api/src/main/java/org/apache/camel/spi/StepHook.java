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
package org.apache.camel.spi;

import org.apache.camel.Exchange;

/**
 * A hook that is asked before every processing step of a route, and can delay the step or fail it. A step is any node
 * of a route, with its node id: {@code to}, {@code process}, {@code log}, {@code split}, {@code choice} and each step
 * inside them, not only the {@code step} EIP.
 * <p/>
 * The hook is installed with {@link org.apache.camel.ExtendedCamelContext#setStepHook(StepHook)}. There is one hook per
 * {@link org.apache.camel.CamelContext}; to use more than one, combine them in one hook. Without a hook, a step costs
 * one check whether a hook is installed (with JMX and an error handler that can redeliver, one more small processor
 * inside the JMX instrumentation of the step makes that check). The {@link org.apache.camel.CamelContext} does not
 * start or stop the hook, also when it is a {@link org.apache.camel.Service}: whoever installs it manages it.
 * <p/>
 * <b>Where it is asked.</b> Each step runs through its {@link org.apache.camel.Channel}, which runs its advices (such
 * as tracing, the debuggers and JMX) and calls the error handler of the step, which calls the processor of the step.
 * The hook is asked right before the processor is called, once for each attempt:
 * <ul>
 * <li>By the error handler of the step, when it is a {@code RedeliveryErrorHandler}: the default error handler, the
 * dead letter channel, the {@code TransactionErrorHandler} of camel-spring and the {@code JtaTransactionErrorHandler}
 * of camel-jta. So a failure from the hook is handled as a failure of the processor: {@code onException}, redelivery
 * and the dead letter channel apply to it, and each redelivery asks the hook again (and waits again for a delay).</li>
 * <li>By the channel, for a step that has no such error handler, such as the steps inside {@code doTry},
 * {@code doCatch}, {@code doFinally} and {@code onException}, a {@code multicast} and its outputs, and every step of a
 * route with {@code noErrorHandler()}. A failure from the hook is then the exception of the exchange, as when the
 * processor fails: inside {@code doTry}, {@code doCatch} gets it; an output of a {@code multicast} is handled (and
 * redelivered) by the error handler that the multicast keeps for each output; otherwise the exchange fails with it,
 * without {@code onException}, redelivery or dead letter channel. The same holds for an error handler that is not a
 * {@code RedeliveryErrorHandler}: the channel asks the hook before it calls that error handler, so a failure from the
 * hook skips it.</li>
 * </ul>
 * <p/>
 * With camel-jta, the {@code transacted} step and its direct children have no error handler of their own, so their
 * channels ask the hook. A failure of such a child, from the hook or from the processor alike, rolls the transaction
 * back and is handled by the {@code JtaTransactionErrorHandler} that wraps the transaction ({@code onException}, and
 * redelivery of the whole transaction, which asks the hook again for each step). A step nested in an EIP inside the
 * transaction (such as a {@code filter} or {@code split}) has the error handler of the route (by default the default
 * error handler), which asks the hook and redelivers the step inside the transaction. A failure from the hook at the
 * {@code transacted} step itself happens before the transaction begins and fails the exchange without that error
 * handler.
 * <p/>
 * In all cases the hook is asked after the advices of the channel and before the {@link InterceptStrategy} wrappers of
 * the step, which wrap its processor: a step that the hook fails also skips the processors of {@code intercept()} for
 * that step, and a delay waits before them.
 * <p/>
 * A step that does not run is not asked: a step that is disabled at runtime ({@link org.apache.camel.DisabledAware}), a
 * step the debugger skips, and a step that an exchange reaches after it was stopped ({@code routeStop}) or while Camel
 * forces a shutdown.
 * <p/>
 * <b>Threads.</b> The hook is called by many threads at the same time, and must be thread-safe. It is called on the
 * thread that runs the step, which also waits for a delay. That can be an I/O thread, such as an event loop thread of
 * an asynchronous component (for example Netty) on which a route goes on after its producer: while it waits, that
 * thread serves nothing else.
 *
 * @since 4.24
 */
@FunctionalInterface
public interface StepHook {

    /**
     * Returned by {@link #beforeStep(Exchange, String)} to call the processor of the step right away.
     */
    long PROCEED = 0;

    /**
     * Called before the processor of a step is called, and again before each redelivery of the step.
     * <p/>
     * To fail the step, the hook sets an exception on the exchange, or throws one: the processor is not called, and the
     * step fails with that exception. As the processor did not run, what it sets on the exchange is not there: for a
     * {@code to} step that failed this way, {@link Exchange#FAILURE_ENDPOINT} is not its endpoint. (An exchange that
     * already has an exception when the step is called, which is rare, is processed as usual unless the hook sets
     * another one.)
     * <p/>
     * A positive return value delays the call of the processor by that many milliseconds: the thread that runs the step
     * waits, as for a slow step, and then calls the processor on the same thread. That is whatever thread runs the
     * step, also a thread of an asynchronous component on which the route goes on (see Threads). It waits with
     * {@link org.apache.camel.util.TimeSource#sleep(long)}, so a test harness that installs a virtual clock there runs
     * the delay in virtual time. When the wait is interrupted, the processor is not called and the step fails with the
     * {@link InterruptedException}; the thread keeps its interrupted status. As for any {@link InterruptedException} on
     * an exchange, Camel marks the exchange as interrupted and stops routing it, instead of handling the failure with
     * {@code onException}, redelivery or the dead letter channel (as when the wait of the redelivery error handler is
     * interrupted).
     *
     * @param  exchange the exchange
     * @param  nodeId   the id of the step: the id of its node in the route, such as {@code to1}, or the id set with
     *                  {@code id("myStep")}
     * @return          what to do with the step, ignored when the hook sets an exception on the exchange or throws one:
     *                  <ul>
     *                  <li>{@link #PROCEED} ({@code 0}): call the processor now;</li>
     *                  <li>a positive value: wait that many milliseconds, then call the processor;</li>
     *                  <li>a negative value: reserved for later use; the step fails with an
     *                  {@link IllegalArgumentException}, without calling the processor.</li>
     *                  </ul>
     */
    long beforeStep(Exchange exchange, String nodeId);

}
