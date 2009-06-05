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
package org.apache.camel;

import java.util.Queue;

/**
 * A consumer of a batch of message exchanges from an {@link Endpoint}
 *
 * @version $Revision$
 */
public interface BatchConsumer extends Consumer {

    /**
     * Sets a maximum number of messages as a limit to poll at each polling.
     * <p/>
     * Can be used to limit eg to 100 to avoid when starting and there are millions
     * of messages for you in the first poll.
     * <p/>
     * Is default unlimited, but use 0 or negative number to disable it as unlimited.
     *
     * @param maxMessagesPerPoll  maximum messages to poll.
     */
    void setMaxMessagesPerPoll(int maxMessagesPerPoll);

    /**
     * Processes the list of {@link org.apache.camel.Exchange} in a batch.
     * <p/>
     * Each message exchange will be processed individually but the batch
     * consumer will add properties with the current index and total in the batch.
     *
     * @param exchanges list of exchanges in this batch
     * @throws Exception if an internal processing error has occurred.
     */
    void processBatch(Queue exchanges) throws Exception;

}
