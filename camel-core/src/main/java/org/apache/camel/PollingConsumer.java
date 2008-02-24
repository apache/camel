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
 * Represents a <a
 * href="http://activemq.apache.org/camel/polling-consumer.html">Polling
 * Consumer</a> where the caller polls for messages when it is ready.
 * 
 * @version $Revision$
 */
public interface PollingConsumer<E extends Exchange> extends Consumer<E> {

    /**
     * Waits until a message is available and then returns it. Warning that this
     * method could block indefinitely if no messages are available.
     * 
     * @return the message exchange received.
     */
    E receive();

    /**
     * Attempts to receive a message exchange immediately without waiting and
     * returning null if a message exchange is not available yet.
     * 
     * @return the message exchange if one is immediately available otherwise
     *         null
     */
    E receiveNoWait();

    /**
     * Attempts to receive a message exchange, waiting up to the given timeout
     * to expire if a message is not yet available
     * 
     * @param timeout the amount of time in milliseconds to wait for a message
     *                before timing out and returning null
     * 
     * @return the message exchange if one iwas available within the timeout
     *         period, or null if the timeout expired
     */
    E receive(long timeout);

}
