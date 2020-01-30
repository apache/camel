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

/**
 * Extended {@link Exchange} which contains the methods and APIs that are not intended for Camel end users
 * but used internally by Camel for optimization purposes, SPI, custom components, or more advanced used-cases with Camel.
 */
public interface ExtendedExchange extends Exchange {

    /**
     * Sets the endpoint which originated this message exchange. This method
     * should typically only be called by {@link Endpoint} implementations
     */
    void setFromEndpoint(Endpoint fromEndpoint);

    /**
     * Sets the route id which originated this message exchange. This method
     * should typically only be called by the internal framework.
     */
    void setFromRouteId(String fromRouteId);

    /**
     * Sets the unit of work that this exchange belongs to; which may map to
     * zero, one or more physical transactions
     */
    void setUnitOfWork(UnitOfWork unitOfWork);

    /**
     * Sets the properties on the exchange
     */
    void setProperties(Map<String, Object> properties);

    /**
     * Adds a {@link org.apache.camel.spi.Synchronization} to be invoked as callback when
     * this exchange is completed.
     *
     * @param onCompletion  the callback to invoke on completion of this exchange
     */
    void addOnCompletion(Synchronization onCompletion);

    /**
     * Checks if the passed {@link Synchronization} instance is
     * already contained on this exchange.
     *
     * @param onCompletion  the callback instance that is being checked for
     * @return <tt>true</tt>, if callback instance is already contained on this exchange, else <tt>false</tt>
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
     * Sets the history node id (the current processor that will process the exchange)
     */
    void setHistoryNodeId(String historyNodeId);

    /**
     * Gets the history node id (the current processor that will process the exchange)
     */
    String getHistoryNodeId();

    /**
     * Sets the history node label (the current processor that will process the exchange)
     */
    void setHistoryNodeLabel(String historyNodeLabel);

    /**
     * Gets the history node label (the current processor that will process the exchange)
     */
    String getHistoryNodeLabel();

    /**
     * Sets whether the exchange is routed in a transaction.
     */
    void setTransacted(boolean transacted);

    /**
     * Whether the exchange is currently used as event notification.
     */
    boolean isNotifyEvent();

    /**
     * Sets whether the exchange is currently used as event notification and if so then this should not
     * generate additional events.
     */
    void setNotifyEvent(boolean notifyEvent);

    /**
     * Whether the exchange was interrupted (InterruptException) during routing.
     */
    boolean isInterrupted();

    /**
     * Used to signal that this exchange was interrupted (InterruptException) during routing.
     */
    void setInterrupted(boolean interrupted);

    /**
     * Whether the exchange has exhausted (attempted all) its redeliveries and still failed.
     * This is used internally by Camel.
     */
    boolean isRedeliveryExhausted();

    /**
     * Used to signal that this exchange has exhausted (attempted all) its redeliveries and still failed.
     * This is used internally by Camel.
     */
    void setRedeliveryExhausted(boolean redeliveryExhausted);

    /**
     * Whether the exchange has been handled by the error handler.
     * This is used internally by Camel.
     */
    boolean isErrorHandlerHandled();

    /**
     * Whether the exchange has been handled by the error handler.
     * This is used internally by Camel.
     */
    Boolean getErrorHandlerHandled();

    /**
     * Used to signal that this exchange has been handled by the error handler.
     * This is used internally by Camel.
     */
    void setErrorHandlerHandled(Boolean errorHandlerHandled);

}
