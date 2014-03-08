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

package org.apache.camel.component.disruptor;
import java.util.concurrent.TimeUnit;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;



/**
 * This interface fuses the EventHandler and LifecycleAware interfaces.
 * It also provides a handle to await the termination of this EventHandler.
 */
interface LifecycleAwareExchangeEventHandler extends EventHandler<ExchangeEvent>, LifecycleAware {

    /**
     * Causes the current thread to wait until the event handler has been
     * started, unless the thread is {@linkplain Thread#interrupt interrupted}.
     * <p/>
     * <p>If the event handler is already started then this method returns
     * immediately.
     * <p/>
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * @throws InterruptedException if the current thread is interrupted
     *                              while waiting
     */
    void awaitStarted() throws InterruptedException;

    /**
     * Causes the current thread to wait until the event handler has been
     * started, unless the thread is {@linkplain Thread#interrupt interrupted},
     * or the specified waiting time elapses.
     * <p/>
     * <p>If the event handler is already started then this method returns
     * immediately with the value {@code true}.
     * <p/>
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; “or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     * <p/>
     * <p>If the specified waiting time elapses then the value {@code false}
     * is returned.  If the time is less than or equal to zero, the method
     * will not wait at all.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the {@code timeout} argument
     * @return {@code true} if the event hanlder is stopped and {@code false}
     *         if the waiting time elapsed before the count reached zero
     * @throws InterruptedException if the current thread is interrupted
     *                              while waiting
     */
    boolean awaitStarted(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Causes the current thread to wait until the event handler has been shut
     * down, unless the thread is {@linkplain Thread#interrupt interrupted}.
     * <p/>
     * <p>If the event handler is not (yet) started then this method returns
     * immediately.
     * <p/>
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * @throws InterruptedException if the current thread is interrupted
     *                              while waiting
     */
    void awaitStopped() throws InterruptedException;

    /**
     * Causes the current thread to wait until the event handler has been shut
     * down, unless the thread is {@linkplain Thread#interrupt interrupted},
     * or the specified waiting time elapses.
     * <p/>
     * <p>If the event handler is not (yet) started then this method returns
     * immediately with the value {@code true}.
     * <p/>
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; “or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     * <p/>
     * <p>If the specified waiting time elapses then the value {@code false}
     * is returned.  If the time is less than or equal to zero, the method
     * will not wait at all.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the {@code timeout} argument
     * @return {@code true} if the event hanlder is stopped and {@code false}
     *         if the waiting time elapsed before the count reached zero
     * @throws InterruptedException if the current thread is interrupted
     *                              while waiting
     */
    boolean awaitStopped(long timeout, TimeUnit unit) throws InterruptedException;
}
