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

import java.util.List;
import java.util.Map;

import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.UnitOfWork;

/*
 * {@link Exchange} extensions which contains the methods and APIs that are not intended for Camel end users but
 * used internally by Camel for optimization purposes, SPI, custom components, or more advanced used-cases with
 * Camel.
 */
public interface ExchangeExtension {
    /**
     * If there is an existing inbound message of the given type then return it as-is, otherwise return null.
     *
     * @param  type the given type
     * @return      the message if exists with the given type, otherwise null.
     */
    <T> T getInOrNull(Class<T> type);

    /**
     * Sets the endpoint which originated this message exchange. This method should typically only be called by
     * {@link Endpoint} implementations
     */
    void setFromEndpoint(Endpoint fromEndpoint);

    /**
     * Returns the endpoint which originated this message exchange. See {@link Exchange#getFromEndpoint()} for details.
     **/
    Endpoint getFromEndpoint();

    /**
     * Sets the route id which originated this message exchange. This method should typically only be called by the
     * internal framework.
     */
    void setFromRouteId(String fromRouteId);

    /**
     * Sets the unit of work that this exchange belongs to; which may map to zero, one or more physical transactions
     */
    void setUnitOfWork(UnitOfWork unitOfWork);

    /**
     * Is stream caching disabled on the given exchange
     */
    boolean isStreamCacheDisabled();

    /**
     * Used to force disabling stream caching which some components can do in special use-cases.
     */
    void setStreamCacheDisabled(boolean streamCacheDisabled);

    /**
     * Adds a {@link org.apache.camel.spi.Synchronization} to be invoked as callback when this exchange is completed.
     *
     * @param onCompletion the callback to invoke on completion of this exchange
     */
    void addOnCompletion(Synchronization onCompletion);

    /**
     * Whether the error handler handled flag has been set.
     */
    boolean isErrorHandlerHandledSet();

    /**
     * Whether the exchange has been handled by the error handler. This is used internally by Camel.
     * <p>
     * Important: Call {@link #isErrorHandlerHandledSet()} first before this method.
     *
     * @see #isErrorHandlerHandledSet()
     */
    boolean isErrorHandlerHandled();

    /**
     * Whether the exchange has been handled by the error handler. This is used internally by Camel.
     */
    Boolean getErrorHandlerHandled();

    /**
     * Used to signal that this exchange has been handled by the error handler. This is used internally by Camel.
     */
    void setErrorHandlerHandled(Boolean errorHandlerHandled);

    /**
     * To control whether the exchange can accept being interrupted currently.
     */
    void setInterruptable(boolean interruptable);

    /**
     * Whether the exchange was interrupted (InterruptException) during routing.
     */
    boolean isInterrupted();

    /**
     * Used to signal that this exchange was interrupted (InterruptException) during routing.
     */
    void setInterrupted(boolean interrupted);

    /**
     * Whether the exchange has exhausted (attempted all) its redeliveries and still failed. This is used internally by
     * Camel.
     */
    boolean isRedeliveryExhausted();

    /**
     * Used to signal that this exchange has exhausted (attempted all) its redeliveries and still failed. This is used
     * internally by Camel.
     */
    void setRedeliveryExhausted(boolean redeliveryExhausted);

    /**
     * Checks if the passed {@link Synchronization} instance is already contained on this exchange.
     *
     * @param  onCompletion the callback instance that is being checked for
     * @return              <tt>true</tt>, if callback instance is already contained on this exchange, else
     *                      <tt>false</tt>
     */
    boolean containsOnCompletion(Synchronization onCompletion);

    /**
     * Handover all the on completions from this exchange to the target exchange.
     */
    void handoverCompletions(Exchange target);

    /**
     * Handover all the on completions from this exchange
     */
    List<Synchronization> handoverCompletions();

    /**
     * Sets the properties on the exchange
     */
    void setProperties(Map<String, Object> properties);

    /**
     * Sets the history node id (the current processor that will process the exchange)
     */
    void setHistoryNodeId(String historyNodeId);

    /**
     * Gets the history node id (the current processor that will process the exchange)
     */
    String getHistoryNodeId();

    /**
     * Gets the history node source:line-number where the node is located in the source code (the current processor that
     * will process the exchange).
     */
    String getHistoryNodeSource();

    /**
     * Sets the history node source:line-number where the node is located in the source code (the current processor that
     * will process the exchange).
     */
    void setHistoryNodeSource(String historyNodeSource);

    /**
     * Gets the history node label (the current processor that will process the exchange)
     */
    String getHistoryNodeLabel();

    /**
     * Sets the history node label (the current processor that will process the exchange)
     */
    void setHistoryNodeLabel(String historyNodeLabel);

    /**
     * Whether the exchange is currently used as event notification.
     */
    boolean isNotifyEvent();

    /**
     * Sets whether the exchange is currently used as event notification and if so then this should not generate
     * additional events.
     */
    void setNotifyEvent(boolean notifyEvent);

    /**
     * To copy the internal properties from this exchange to the target exchange
     * <p/>
     * This method is only intended for Camel internally.
     *
     * @param target the target exchange
     */
    void copyInternalProperties(Exchange target);

    /**
     * To get a property that was copied specially (thread safe with deep cloning).
     *
     * @see SafeCopyProperty
     */
    <T> T getSafeCopyProperty(String key, Class<T> type);

    /**
     * To set a property that must be copied specially (thread safe with deep cloning).
     *
     * @see SafeCopyProperty
     */
    void setSafeCopyProperty(String key, SafeCopyProperty value);

    /**
     * Copy the safe copy properties from this exchange to the target exchange
     */
    void copySafeCopyPropertiesTo(ExchangeExtension target);

    /**
     * Gets the internal properties from this exchange. The known set of internal keys is defined in
     * {@link ExchangePropertyKey}.
     * <p/>
     * This method is only intended for Camel internally.
     *
     * @return all the internal properties in a Map
     */
    Map<String, Object> getInternalProperties();

    /**
     * Sets whether the exchange is routed in a transaction.
     */
    void setTransacted(boolean transacted);

    /**
     * Callback used by {@link Consumer} if the consumer is completing the exchange processing with default behaviour.
     * <p>
     * This is only used when pooled exchange is enabled for optimization and reducing object allocations.
     */
    AsyncCallback getDefaultConsumerCallback();

    /**
     * Callback used by {@link Consumer} if the consumer is completing the exchange processing with default behaviour.
     * <p>
     * This is only used when pooled exchange is enabled for optimization and reducing object allocations.
     */
    void setDefaultConsumerCallback(AsyncCallback callback);

    /**
     * Returns whether the exchange has been failure handed
     *
     * @return true if failure handled or false otherwise
     */
    boolean isFailureHandled();

    /**
     * Sets whether the exchange has been failure handled
     *
     * @param failureHandled true if failure handled or false otherwise
     */
    void setFailureHandled(boolean failureHandled);

    /**
     * Create a new exchange copied from this, with the context set to the given context
     *
     * @param  context the context associated with the new exchange
     * @return         A new Exchange instance
     */
    Exchange createCopyWithProperties(CamelContext context);
}
