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
 * Strategy for a {@link org.apache.camel.PollingConsumer} when polling an {@link org.apache.camel.Endpoint}.
 * <p/>
 * This pluggable strategy allows to plugin different implementations what to do, most noticeable what to
 * do in case the polling goes wrong. This can be handled in the {@link #rollback(Consumer, Endpoint, Exception) rollback}
 * method.
 *
 * @version $Revision$
 */
public interface PollingConsumerPollStrategy {

    /**
     * Called when poll is about to begin
     *
     * @param consumer the consumer
     * @param endpoint the endpoint being consumed
     */
    void begin(Consumer consumer, Endpoint endpoint);

    /**
     * Called when poll is completed sucesfully
     *
     * @param consumer the consumer
     * @param endpoint the endpoint being consumed
     */
    void commit(Consumer consumer, Endpoint endpoint);

    /**
     * Called when poll failed
     *
     * @param consumer the consumer
     * @param endpoint the endpoint being consumed
     * @param cause the caused exception
     * @throws Exception can be used to rethrow the caused exception
     */
    void rollback(Consumer consumer, Endpoint endpoint, Exception cause) throws Exception;

}
