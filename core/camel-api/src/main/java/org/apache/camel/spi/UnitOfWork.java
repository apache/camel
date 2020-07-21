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

import java.util.function.Predicate;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.Service;

/**
 * An object representing the unit of work processing an {@link Exchange}
 * which allows the use of {@link Synchronization} hooks. This object might map one-to-one with
 * a transaction in JPA or Spring; or might not.
 */
public interface UnitOfWork extends Service {

    String MDC_BREADCRUMB_ID = "camel.breadcrumbId";
    String MDC_EXCHANGE_ID = "camel.exchangeId";
    String MDC_MESSAGE_ID = "camel.messageId";
    String MDC_CORRELATION_ID = "camel.correlationId";
    String MDC_ROUTE_ID = "camel.routeId";
    String MDC_STEP_ID = "camel.stepId";
    String MDC_CAMEL_CONTEXT_ID = "camel.contextId";
    String MDC_TRANSACTION_KEY = "camel.transactionKey";

    /**
     * Adds a synchronization hook
     *
     * @param synchronization the hook
     */
    void addSynchronization(Synchronization synchronization);

    /**
     * Removes a synchronization hook
     *
     * @param synchronization the hook
     */
    void removeSynchronization(Synchronization synchronization);

    /**
     * Checks if the passed synchronization hook is already part of this unit of work.
     *
     * @param synchronization the hook
     * @return <tt>true</tt>, if the passed synchronization is part of this unit of work, else <tt>false</tt>
     */
    boolean containsSynchronization(Synchronization synchronization);

    /**
     * Handover all the registered synchronizations to the target {@link org.apache.camel.Exchange}.
     * <p/>
     * This is used when a route turns into asynchronous and the {@link org.apache.camel.Exchange} that
     * is continued and routed in the async thread should do the on completion callbacks instead of the
     * original synchronous thread.
     *
     * @param target the target exchange
     */
    void handoverSynchronization(Exchange target);

    /**
     * Handover all the registered synchronizations to the target {@link org.apache.camel.Exchange}.
     * <p/>
     * This is used when a route turns into asynchronous and the {@link org.apache.camel.Exchange} that
     * is continued and routed in the async thread should do the on completion callbacks instead of the
     * original synchronous thread.
     *
     * @param target the target exchange
     * @param filter optional filter to only handover if filter returns <tt>true</tt>
     */
    void handoverSynchronization(Exchange target, Predicate<Synchronization> filter);

    /**
     * Invoked when this unit of work has been completed, whether it has failed or completed
     *
     * @param exchange the current exchange
     */
    void done(Exchange exchange);

    /**
     * Invoked when this unit of work is about to be routed by the given route.
     *
     * @param exchange the current exchange
     * @param route    the route
     */
    void beforeRoute(Exchange exchange, Route route);

    /**
     * Invoked when this unit of work is done being routed by the given route.
     *
     * @param exchange the current exchange
     * @param route    the route
     */
    void afterRoute(Exchange exchange, Route route);

    /**
     * Gets the original IN {@link Message} this Unit of Work was started with.
     * <p/>
     * The original message is only returned if the option {@link org.apache.camel.RuntimeConfiguration#isAllowUseOriginalMessage()}
     * is enabled. If its disabled an <tt>IllegalStateException</tt> is thrown.
     *
     * @return the original IN {@link Message}, or <tt>null</tt> if using original message is disabled.
     */
    Message getOriginalInMessage();

    /**
     * Are we transacted?
     *
     * @return <tt>true</tt> if transacted, <tt>false</tt> otherwise
     */
    boolean isTransacted();

    /**
     * Are we already transacted by the given transaction key?
     *
     * @param key the transaction key
     * @return <tt>true</tt> if already, <tt>false</tt> otherwise
     */
    boolean isTransactedBy(Object key);

    /**
     * Mark this UnitOfWork as being transacted by the given transaction key.
     * <p/>
     * When the transaction is completed then invoke the {@link #endTransactedBy(Object)} method using the same key.
     *
     * @param key the transaction key
     */
    void beginTransactedBy(Object key);

    /**
     * Mark this UnitOfWork as not transacted anymore by the given transaction definition.
     *
     * @param key the transaction key
     */
    void endTransactedBy(Object key);

    /**
     * Gets the {@link Route} that this {@link UnitOfWork} currently is being routed through.
     * <p/>
     * Notice that an {@link Exchange} can be routed through multiple routes and thus the
     * {@link org.apache.camel.Route} can change over time.
     *
     * @return the route, maybe be <tt>null</tt> if not routed through a route currently.
     */
    Route getRoute();

    /**
     * Pushes the {@link Route} that this {@link UnitOfWork} currently is being routed through.
     * <p/>
     * Notice that an {@link Exchange} can be routed through multiple routes and thus the
     * {@link org.apache.camel.Route} can change over time.
     *
     * @param route the route
     */
    void pushRoute(Route route);

    /**
     * When finished being routed under the current {@link org.apache.camel.Route}
     * it should be removed.
     *
     * @return the route or <tt>null</tt> if none existed
     */
    Route popRoute();

    /**
     * Whether the unit of work should call the before/after process methods or not.
     */
    boolean isBeforeAfterProcess();

    /**
     * Strategy for work to be execute before processing.
     * <p/>
     * For example the MDCUnitOfWork leverages this
     * to ensure MDC is handled correctly during routing exchanges using the
     * asynchronous routing engine.
     * <p/>
     * This requires {@link #isBeforeAfterProcess()} returns <tt>true</tt> to be enabled.
     *
     * @param processor the processor to be executed
     * @param exchange  the current exchange
     * @param callback  the callback
     * @return the callback to be used (can return a wrapped callback)
     */
    AsyncCallback beforeProcess(Processor processor, Exchange exchange, AsyncCallback callback);

    /**
     * Strategy for work to be executed after the processing
     * <p/>
     * This requires {@link #isBeforeAfterProcess()} returns <tt>true</tt> to be enabled.
     *
     * @param processor the processor executed
     * @param exchange  the current exchange
     * @param callback  the callback used
     * @param doneSync  whether the process was done synchronously or asynchronously
     */
    void afterProcess(Processor processor, Exchange exchange, AsyncCallback callback, boolean doneSync);

    /**
     * Create a child unit of work, which is associated to this unit of work as its parent.
     * <p/>
     * This is often used when EIPs need to support child unit of works. For example a splitter,
     * where the sub messages of the splitter all participate in the same sub unit of work.
     * That sub unit of work then decides whether the Splitter (in general) is failed or a
     * processed successfully.
     *
     * @param childExchange the child exchange
     * @return the created child unit of work
     */
    UnitOfWork createChildUnitOfWork(Exchange childExchange);

    /**
     * Sets the parent unit of work.
     *
     * @param parentUnitOfWork the parent
     */
    void setParentUnitOfWork(UnitOfWork parentUnitOfWork);

}
