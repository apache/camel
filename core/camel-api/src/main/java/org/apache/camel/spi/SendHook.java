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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

/**
 * A hook that runs around every call of the producer of an {@link Endpoint}, whatever started the send: a route
 * ({@code to}, {@code toD}, {@code wireTap}, {@code enrich}, {@code recipientList}, {@code routingSlip},
 * {@code dynamicRouter}), a {@link org.apache.camel.ProducerTemplate} or a deferred producer. Before the producer is
 * called, the hook can change the message, stop the send, skip it or delay it. After the producer is done, it can
 * change the reply.
 * <p/>
 * The hook is installed with {@link org.apache.camel.ExtendedCamelContext#setSendHook(SendHook)}. There is one hook per
 * {@link org.apache.camel.CamelContext}; to use more than one, combine them in one hook. Without a hook, a send costs
 * one check whether a hook is installed.
 * <p/>
 * <b>Where it runs.</b> The hook runs right around the call of the producer, inside the error handler that handles a
 * failure of that producer: the error handler of the route for {@code to}, {@code toD} and {@code enrich}, the error
 * handler of the tapped copy for {@code wireTap}, and the error handler of the recipient or of the step for
 * {@code recipientList}, {@code routingSlip} and {@code dynamicRouter}. So an exception set by
 * {@link #beforeSend(Exchange, Endpoint)} is handled as a failure of the producer: {@code onException}, redelivery and
 * the dead letter channel apply to it, and each redelivery runs the hook again; inside {@code doTry}, {@code doCatch}
 * gets it. It runs after the {@link CamelEvent.ExchangeSendingEvent} and before the
 * {@link CamelEvent.ExchangeSentEvent}, so the time taken of the send includes a delay of the hook. A send from a
 * {@link org.apache.camel.ProducerTemplate} has no error handler: the exception is returned to the caller.
 * <p/>
 * <b>Threads.</b> The hook is called by many threads at the same time, and must be thread-safe.
 * {@link #beforeSend(Exchange, Endpoint)} runs on the thread that sends. {@link #afterSend(Exchange, Endpoint)} runs on
 * the thread that completes the send, which can be another thread for an asynchronous producer or after a delay.
 * <p/>
 * <b>Contract.</b> {@link #afterSend(Exchange, Endpoint)} is called exactly once for every call of
 * {@link #beforeSend(Exchange, Endpoint)} that returned: after the producer is done, or right away when the hook
 * stopped or skipped the send. It is also called when the producer throws an exception instead of completing; the
 * exception is then set on the exchange. When {@link #beforeSend(Exchange, Endpoint)} throws an exception, the
 * exception is set on the exchange, the producer is not called and {@link #afterSend(Exchange, Endpoint)} is not
 * called. When {@link #afterSend(Exchange, Endpoint)} throws an exception, it is set on the exchange (or added as a
 * suppressed exception when the exchange has failed already). In every case the send completes exactly once.
 *
 * @since 4.24
 */
public interface SendHook {

    /**
     * Returned by {@link #beforeSend(Exchange, Endpoint)} to call the producer right away.
     */
    long PROCEED = 0;

    /**
     * Returned by {@link #beforeSend(Exchange, Endpoint)} to complete the send without calling the producer, as if the
     * producer had succeeded. The exchange is left as the hook left it. {@link #afterSend(Exchange, Endpoint)} is
     * called, and the {@link CamelEvent.ExchangeSendingEvent} and {@link CamelEvent.ExchangeSentEvent} are fired as for
     * any other send.
     */
    long SKIP = -1;

    /**
     * Called before the producer of the endpoint is called.
     * <p/>
     * The hook can change the message of the exchange, which the producer then receives. To stop the send it sets an
     * exception on the exchange: the producer is not called, and the send fails with that exception. (An exchange that
     * already has an exception when it is sent, which is rare, is sent as usual unless the hook sets another one.)
     * <p/>
     * A positive return value delays the call of the producer by that many milliseconds. The delay does not block the
     * thread that sends: the producer is called from the scheduled thread pool that the error handler uses for
     * asynchronous redelivery, and the send completes asynchronously. A transacted exchange must stay on its thread, so
     * for a transacted exchange the thread that sends waits, as a redelivery of a transacted exchange does.
     *
     * @param  exchange the exchange that is sent
     * @param  endpoint the endpoint it is sent to
     * @return          {@link #PROCEED} to call the producer now, a delay in milliseconds to call it later, or
     *                  {@link #SKIP} to complete the send without calling it. Other negative values are the same as
     *                  {@link #PROCEED}. The value is ignored when the hook sets an exception on the exchange.
     */
    long beforeSend(Exchange exchange, Endpoint endpoint);

    /**
     * Called after the producer of the endpoint is done, or right away when {@link #beforeSend(Exchange, Endpoint)}
     * stopped or skipped the send.
     * <p/>
     * The hook can change the reply (the message of the exchange), or set or clear the exception of the exchange.
     *
     * @param exchange the exchange that was sent
     * @param endpoint the endpoint it was sent to
     */
    void afterSend(Exchange exchange, Endpoint endpoint);

}
