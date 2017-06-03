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
     * Returns the unique ID of this unit of work, lazily creating one if it does not yet have one
     *
     * @return the unique ID
     */
    String getId();

    /**
     * Gets the original IN {@link Message} this Unit of Work was started with.
     * <p/>
     * The original message is only returned if the option {@link org.apache.camel.RuntimeConfiguration#isAllowUseOriginalMessage()}
     * is enabled. If its disabled, then <tt>null</tt> is returned.
     *
     * @return the original IN {@link Message}, or <tt>null</tt> if using original message is disabled.
     */
    Message getOriginalInMessage();

    /**
     * Gets tracing information
     *
     * @return trace information
     */
    @Deprecated
    TracedRouteNodes getTracedRouteNodes();

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
     * Gets the {@link RouteContext} that this {@link UnitOfWork} currently is being routed through.
     * <p/>
     * Notice that an {@link Exchange} can be routed through multiple routes and thus the
     * {@link org.apache.camel.spi.RouteContext} can change over time.
     *
     * @return the route context
     * @see #pushRouteContext(RouteContext)
     * @see #popRouteContext()
     */
    RouteContext getRouteContext();

    /**
     * Pushes the {@link RouteContext} that this {@link UnitOfWork} currently is being routed through.
     * <p/>
     * Notice that an {@link Exchange} can be routed through multiple routes and thus the
     * {@link org.apache.camel.spi.RouteContext} can change over time.
     *
     * @param routeContext the route context
     */
    void pushRouteContext(RouteContext routeContext);

    /**
     * When finished being routed under the current {@link org.apache.camel.spi.RouteContext}
     * it should be removed.
     *
     * @return the route context or <tt>null</tt> if none existed
     */
    RouteContext popRouteContext();

    /**
     * Strategy for optional work to be execute before processing
     * <p/>
     * For example the {@link org.apache.camel.impl.MDCUnitOfWork} leverages this
     * to ensure MDC is handled correctly during routing exchanges using the
     * asynchronous routing engine.
     *
     * @param processor the processor to be executed
     * @param exchange  the current exchange
     * @param callback  the callback
     * @return the callback to be used (can return a wrapped callback)
     */
    AsyncCallback beforeProcess(Processor processor, Exchange exchange, AsyncCallback callback);

    /**
     * Strategy for optional work to be executed after the processing
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
     * This is often used when EIPs need to support {@link SubUnitOfWork}s. For example a splitter,
     * where the sub messages of the splitter all participate in the same sub unit of work.
     * That sub unit of work then decides whether the Splitter (in general) is failed or a
     * processed successfully.
     *
     * @param childExchange the child exchange
     * @return the created child unit of work
     * @see SubUnitOfWork
     * @see SubUnitOfWorkCallback
     */
    UnitOfWork createChildUnitOfWork(Exchange childExchange);

    /**
     * Sets the parent unit of work.
     *
     * @param parentUnitOfWork the parent
     */
    void setParentUnitOfWork(UnitOfWork parentUnitOfWork);

    /**
     * Gets the {@link SubUnitOfWorkCallback} if this unit of work participates in a sub unit of work.
     *
     * @return the callback, or <tt>null</tt> if this unit of work is not part of a sub unit of work.
     * @see #beginSubUnitOfWork(org.apache.camel.Exchange)
     */
    SubUnitOfWorkCallback getSubUnitOfWorkCallback();

    /**
     * Begins a {@link SubUnitOfWork}, where sub (child) unit of works participate in a parent unit of work.
     * The {@link SubUnitOfWork} will callback to the parent unit of work using {@link SubUnitOfWorkCallback}s.
     *
     * @param exchange the exchange
     */
    void beginSubUnitOfWork(Exchange exchange);

    /**
     * Ends a {@link SubUnitOfWork}.
     * <p/>
     * The {@link #beginSubUnitOfWork(org.apache.camel.Exchange)} must have been invoked
     * prior to this operation.
     *
     * @param exchange the exchange
     */
    void endSubUnitOfWork(Exchange exchange);

}
