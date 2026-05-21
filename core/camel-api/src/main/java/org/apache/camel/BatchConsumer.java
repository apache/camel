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

import java.util.Queue;

/**
 * A {@link Consumer} that receives messages from an {@link Endpoint} in discrete batches rather than one at a time.
 * <p/>
 * Batch consumers expose a {@link #setMaxMessagesPerPoll(int)} limit to bound the number of messages fetched in a
 * single polling cycle, which helps control memory usage and startup latency.
 * <p/>
 * During graceful shutdown, the {@link ShutdownRunningTask} option controls whether a batch in progress is completed in
 * full or interrupted after the current message.
 *
 * @see Consumer
 * @see ShutdownRunningTask
 */
public interface BatchConsumer extends Consumer {

    /**
     * Sets a maximum number of messages as a limit to poll at each polling.
     * <p/>
     * Can be used to limit e.g. to 100 to avoid reading thousands or more messages within the first polling at startup.
     * <p/>
     * Is default unlimited, but use 0 or negative number to disable it as unlimited.
     *
     * @param maxMessagesPerPoll maximum messages to poll.
     */
    void setMaxMessagesPerPoll(int maxMessagesPerPoll);

    /**
     * Processes the list of {@link org.apache.camel.Exchange} objects in a batch.
     * <p/>
     * Each message exchange will be processed individually but the batch consumer will add properties with the current
     * index and total in the batch. The items in the Queue may actually be Holder objects that store other data
     * alongside the Exchange.
     *
     * @param  exchanges list of items in this batch
     * @return           number of messages actually processed
     * @throws Exception if an internal processing error has occurred.
     */
    int processBatch(Queue<Object> exchanges) throws Exception;

    /**
     * Whether processing the batch is still allowed.
     * <p/>
     * This is used during shutdown to indicate whether to complete the pending exchanges or stop after the current
     * exchange has been processed.
     *
     * @return <tt>true</tt> to continue processing from the batch, or <tt>false</tt> to stop.
     * @see    org.apache.camel.ShutdownRunningTask
     */
    boolean isBatchAllowed();
}
