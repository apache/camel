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
package org.apache.camel;

import java.util.concurrent.CompletableFuture;

/**
 * An <b>asynchronous</b> processor which can process an {@link Exchange} in an asynchronous fashion and signal
 * completion by invoking the {@link AsyncCallback}.
 * <p/>
 * Any processor can be coerced to have an {@link AsyncProcessor} interface by using the
 * {@link org.apache.camel.support.AsyncProcessorConverterHelper#convert AsyncProcessorConverterHelper.convert} method.
 * <p/>
 * <b>The contract of {@link #process(Exchange, AsyncCallback)}.</b> The routing engine relies on it, and a processor
 * that breaks the first two rules is a bug, whether it is Camel's or a user's:
 * <ul>
 * <li>The callback is called exactly once for each call of {@code process}: also on a failure, and never a second time
 * (for example once from a timeout and again from a reply that comes late). A second call can continue an exchange that
 * was already continued.</li>
 * <li>{@code process} does not throw. On a failure it sets the exception on the exchange
 * ({@link Exchange#setException(Throwable)}) and calls the callback.</li>
 * <li>The value the callback is called with ({@code doneSync}) is the value {@code process} returns. When
 * {@code process} returns {@code true}, the callback has been called already, on the calling thread; when it returns
 * {@code false}, the callback is called with {@code false}, possibly on another thread and possibly before
 * {@code process} returns. A processor that hands its callback to the {@link org.apache.camel.spi.ReactiveExecutor}
 * instead of calling it, as the channel of each step of a route does, is the exception: it returns the value of the
 * processor it calls, and the reactive executor runs its callback with {@code false}, at once or after the work the
 * thread is running. So a caller of such a processor must go on through the callback, not through the return
 * value.</li>
 * </ul>
 *
 * @see Processor
 * @see AsyncCallback
 */
public interface AsyncProcessor extends Processor {

    /**
     * Processes the message exchange. Similar to {@link Processor#process}, but the caller supports having the exchange
     * asynchronously processed.
     * <p/>
     * If there was a failure processing then the caused {@link Exception} would be set on the {@link Exchange}, and the
     * callback is called; this method does not throw. See the contract in the class Javadoc.
     *
     * @param  exchange the message exchange
     * @param  callback the {@link AsyncCallback} will be invoked exactly once when the processing of the exchange is
     *                  completed. If the exchange is completed synchronously, then the callback is also invoked
     *                  synchronously. The callback should therefore be careful of starting recursive loop.
     * @return          (doneSync) <tt>true</tt> to continue execution synchronously, <tt>false</tt> to continue being
     *                  executed asynchronously; the same value the callback is called with, except for a processor that
     *                  hands its callback to the reactive executor (see above)
     */
    boolean process(Exchange exchange, AsyncCallback callback);

    CompletableFuture<Exchange> processAsync(Exchange exchange);

}
