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
 * Template for working with Camel and consuming {@link Message} instances in an
 * {@link Exchange} from an {@link Endpoint}.
 * <br/>
 * <p/>This template is an implementation of the
 * <a href="http://camel.apache.org/polling-consumer.html">Polling Consumer EIP</a>.
 * This is <b>not</b> the <a href="http://camel.apache.org/event-driven-consumer.html">Event Driven Consumer EIP</a>.
 * <br/>
 * <p/>The {@link ConsumerTemplate} is <b>thread safe</b>.
 * <br/>
 * <p/><b>All</b> methods throws {@link RuntimeCamelException} if consuming of
 * the {@link Exchange} failed and an Exception occurred. The <tt>getCause</tt>
 * method on {@link RuntimeCamelException} returns the wrapper original caused
 * exception.
 * <br/>
 * <p/>All the receive<b>Body</b> methods will return the content according to this strategy
 * <ul>
 *   <li>throws {@link RuntimeCamelException} as stated above</li>
 *   <li>The <tt>fault.body</tt> if there is a fault message set and its not <tt>null</tt></li>
 *   <li>The <tt>out.body</tt> if there is a out message set and its not <tt>null</tt></li>
 *   <li>The <tt>in.body</tt></li>
 * </ul>
 * <br/>
 * <p/>Before using the template it must be started.
 * And when you are done using the template, make sure to {@link #stop()} the template.
 * <br/>
 * <p/><b>Important note on usage:</b> See this
 * <a href="http://camel.apache.org/why-does-camel-use-too-many-threads-with-producertemplate.html">FAQ entry</a>
 * before using, it applies to this {@link ConsumerTemplate} as well.
 *
 * @see ProducerTemplate
 * @see FluentProducerTemplate
 */
public interface ConsumerTemplate extends Service {

    /**
     * Get the {@link CamelContext}
     *
     * @return camelContext the Camel context
     */
    CamelContext getCamelContext();

    // Configuration methods
    // -----------------------------------------------------------------------

    /**
     * Gets the maximum cache size used.
     *
     * @return the maximum cache size
     */
    int getMaximumCacheSize();

    /**
     * Sets a custom maximum cache size.
     *
     * @param maximumCacheSize the custom maximum cache size
     */
    void setMaximumCacheSize(int maximumCacheSize);

    /**
     * Gets an approximated size of the current cached resources in the backing cache pools.
     *
     * @return the size of current cached resources
     */
    int getCurrentCacheSize();

    /**
     * Cleanup the cache (purging stale entries)
     */
    void cleanUp();

    // Synchronous methods
    // -----------------------------------------------------------------------

    /**
     * Receives from the endpoint, waiting until there is a response
     * <p/>
     * <b>Important:</b> See {@link #doneUoW(Exchange)}
     *
     * @param endpointUri the endpoint to receive from
     * @return the returned exchange
     */
    Exchange receive(String endpointUri);

    /**
     * Receives from the endpoint, waiting until there is a response.
     * <p/>
     * <b>Important:</b> See {@link #doneUoW(Exchange)}
     *
     * @param endpoint the endpoint to receive from
     * @return the returned exchange
     * @see #doneUoW(Exchange)
     */
    Exchange receive(Endpoint endpoint);

    /**
     * Receives from the endpoint, waiting until there is a response
     * or the timeout occurs
     * <p/>
     * <b>Important:</b> See {@link #doneUoW(Exchange)}
     *
     * @param endpointUri the endpoint to receive from
     * @param timeout     timeout in millis to wait for a response
     * @return the returned exchange, or <tt>null</tt> if no response
     * @see #doneUoW(Exchange)
     */
    Exchange receive(String endpointUri, long timeout);

    /**
     * Receives from the endpoint, waiting until there is a response
     * or the timeout occurs
     * <p/>
     * <b>Important:</b> See {@link #doneUoW(Exchange)}
     *
     * @param endpoint the endpoint to receive from
     * @param timeout  timeout in millis to wait for a response
     * @return the returned exchange, or <tt>null</tt> if no response
     * @see #doneUoW(Exchange)
     */
    Exchange receive(Endpoint endpoint, long timeout);

    /**
     * Receives from the endpoint, not waiting for a response if non exists.
     * <p/>
     * <b>Important:</b> See {@link #doneUoW(Exchange)}
     *
     * @param endpointUri the endpoint to receive from
     * @return the returned exchange, or <tt>null</tt> if no response
     */
    Exchange receiveNoWait(String endpointUri);

    /**
     * Receives from the endpoint, not waiting for a response if non exists.
     * <p/>
     * <b>Important:</b> See {@link #doneUoW(Exchange)}
     *
     * @param endpoint the endpoint to receive from
     * @return the returned exchange, or <tt>null</tt> if no response
     */
    Exchange receiveNoWait(Endpoint endpoint);

    /**
     * Receives from the endpoint, waiting until there is a response
     *
     * @param endpointUri the endpoint to receive from
     * @return the returned response body
     */
    Object receiveBody(String endpointUri);

    /**
     * Receives from the endpoint, waiting until there is a response
     *
     * @param endpoint the endpoint to receive from
     * @return the returned response body
     */
    Object receiveBody(Endpoint endpoint);

    /**
     * Receives from the endpoint, waiting until there is a response
     * or the timeout occurs
     *
     * @param endpointUri the endpoint to receive from
     * @param timeout     timeout in millis to wait for a response
     * @return the returned response body, or <tt>null</tt> if no response
     */
    Object receiveBody(String endpointUri, long timeout);

    /**
     * Receives from the endpoint, waiting until there is a response
     * or the timeout occurs
     *
     * @param endpoint the endpoint to receive from
     * @param timeout  timeout in millis to wait for a response
     * @return the returned response body, or <tt>null</tt> if no response
     */
    Object receiveBody(Endpoint endpoint, long timeout);

    /**
     * Receives from the endpoint, not waiting for a response if non exists.
     *
     * @param endpointUri the endpoint to receive from
     * @return the returned response body, or <tt>null</tt> if no response
     */
    Object receiveBodyNoWait(String endpointUri);

    /**
     * Receives from the endpoint, not waiting for a response if non exists.
     *
     * @param endpoint the endpoint to receive from
     * @return the returned response body, or <tt>null</tt> if no response
     */
    Object receiveBodyNoWait(Endpoint endpoint);

    /**
     * Receives from the endpoint, waiting until there is a response
     *
     * @param endpointUri the endpoint to receive from
     * @param type        the expected response type
     * @return the returned response body
     */
    <T> T receiveBody(String endpointUri, Class<T> type);

    /**
     * Receives from the endpoint, waiting until there is a response
     *
     * @param endpoint the endpoint to receive from
     * @param type     the expected response type
     * @return the returned response body
     */
    <T> T receiveBody(Endpoint endpoint, Class<T> type);

    /**
     * Receives from the endpoint, waiting until there is a response
     * or the timeout occurs
     *
     * @param endpointUri the endpoint to receive from
     * @param timeout     timeout in millis to wait for a response
     * @param type        the expected response type
     * @return the returned response body, or <tt>null</tt> if no response
     */
    <T> T receiveBody(String endpointUri, long timeout, Class<T> type);

    /**
     * Receives from the endpoint, waiting until there is a response
     * or the timeout occurs
     *
     * @param endpoint the endpoint to receive from
     * @param timeout  timeout in millis to wait for a response
     * @param type     the expected response type
     * @return the returned response body, or <tt>null</tt> if no response
     */
    <T> T receiveBody(Endpoint endpoint, long timeout, Class<T> type);

    /**
     * Receives from the endpoint, not waiting for a response if non exists.
     *
     * @param endpointUri the endpoint to receive from
     * @param type        the expected response type
     * @return the returned response body, or <tt>null</tt> if no response
     */
    <T> T receiveBodyNoWait(String endpointUri, Class<T> type);

    /**
     * Receives from the endpoint, not waiting for a response if non exists.
     *
     * @param endpoint the endpoint to receive from
     * @param type     the expected response type
     * @return the returned response body, or <tt>null</tt> if no response
     */
    <T> T receiveBodyNoWait(Endpoint endpoint, Class<T> type);

    /**
     * If you have used any of the <tt>receive</tt> methods which returns a {@link Exchange} type
     * then you need to invoke this method when you are done using the returned {@link Exchange}.
     * <p/>
     * This is needed to ensure any {@link org.apache.camel.spi.Synchronization} works is being executed.
     * For example if you consumed from a file endpoint, then the consumed file is only moved/delete when
     * you done the {@link Exchange}.
     * <p/>
     * Note for all the other <tt>receive</tt> methods which does <b>not</b> return a {@link Exchange} type,
     * the done has been executed automatic by Camel itself.
     *
     * @param exchange  the exchange
     */
    void doneUoW(Exchange exchange);

}
