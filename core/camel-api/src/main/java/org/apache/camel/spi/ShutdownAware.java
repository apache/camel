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

import org.apache.camel.ShutdownRunningTask;

/**
 * Allows {@link org.apache.camel.Consumer} to fine grained control on shutdown which mostly
 * have to cater for in-memory based components. These components need to be able to have an extra
 * chance to have their pending exchanges being completed to support graceful shutdown. This helps
 * ensure that no messages get lost.
 * @see org.apache.camel.spi.ShutdownStrategy
 */
public interface ShutdownAware extends ShutdownPrepared {

    /**
     * To defer shutdown during first phase of shutdown. This allows any pending exchanges to be completed
     * and therefore ensure a graceful shutdown without loosing messages. At the very end when there are no
     * more inflight and pending messages the consumer could then safely be shutdown.
     * <p/>
     * This is needed by {@link org.apache.camel.component.seda.SedaConsumer}.
     *
     * @param shutdownRunningTask the configured option for how to act when shutting down running tasks.
     * @return <tt>true</tt> to defer shutdown to very last.
     */
    boolean deferShutdown(ShutdownRunningTask shutdownRunningTask);

    /**
     * Gets the number of pending exchanges.
     * <p/>
     * Some consumers has internal queues with {@link org.apache.camel.Exchange} which are pending.
     * For example the {@link org.apache.camel.component.seda.SedaConsumer}.
     * <p/>
     * Return <tt>zero</tt> to indicate no pending exchanges and therefore ready to shutdown.
     *
     * @return number of pending exchanges
     */
    int getPendingExchangesSize();

}
