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

/**
 * Strategy that allows {@link Consumer}s to influence the {@link PollingConsumer}.
 * <p/>
 * For example this is used by schedule based consumers to be able to suspend/resume
 * upon polling using a {@link PollingConsumer}.
 *
 * @see org.apache.camel.impl.EventDrivenPollingConsumer
 */
public interface PollingConsumerPollingStrategy {

    /**
     * Callback invoked when the consumer is initialized such as when the {@link PollingConsumer} starts.
     *
     * @throws Exception can be thrown if error initializing.
     */
    void onInit() throws Exception;

    /**
     * Callback invoked before the poll.
     *
     * @param timeout the timeout
     * @throws Exception can be thrown if error occurred
     * @return timeout to be used, this allows returning a higher timeout value
     * to ensure at least one poll is being performed
     */
    long beforePoll(long timeout) throws Exception;

    /**
     * Callback invoked after the poll.
     *
     * @throws Exception can be thrown if error occurred
     */
    void afterPoll() throws Exception;
}
