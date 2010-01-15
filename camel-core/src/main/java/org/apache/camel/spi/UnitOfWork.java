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

import org.apache.camel.Exchange;
import org.apache.camel.Message;

/**
 * An object representing the unit of work processing an {@link Exchange}
 * which allows the use of {@link Synchronization} hooks. This object might map one-to-one with
 * a transaction in JPA or Spring; or might not.
 *
 * @version $Revision$
 */
public interface UnitOfWork {

    /**
     * Adds a synchronization hook
     *
     * @param synchronization  the hook
     */
    void addSynchronization(Synchronization synchronization);

    /**
     * Removes a synchronization hook
     *
     * @param synchronization  the hook
     */
    void removeSynchronization(Synchronization synchronization);

    /**
     * Handover all the registered synchronizations to the target {@link org.apache.camel.Exchange}.
     * <p/>
     * This is used when a route turns into asynchronous and the {@link org.apache.camel.Exchange} that
     * is continued and routed in the async thread should do the on completion callbacks instead of the
     * original synchronous thread.
     * 
     * @param target  the target exchange
     */
    void handoverSynchronization(Exchange target);

    /**
     * Invoked when this unit of work has been completed, whether it has failed or completed
     *
     * @param exchange the current exchange
     */
    void done(Exchange exchange);

    /**
     * Returns the unique ID of this unit of work, lazily creating one if it does not yet have one
     * 
     * @return the unique ID
     */
    String getId();

    /**
     * Gets the original IN {@link Message} this Unit of Work was started with.
     *
     * @return the original IN {@link Message}
     */
    Message getOriginalInMessage();

    /**
     * Gets tracing information
     *
     * @return trace information
     */
    TracedRouteNodes getTracedRouteNodes();

    /**
     * Are we already transacted by the given transaction definition
     * <p/>
     * The definition will most likely be a Spring TransactionTemplate when using Spring Transaction
     *
     * @param transactionDefinition the transaction definition
     * @return <tt>true</tt> if already, <tt>false</tt> otherwise
     */
    boolean isTransactedBy(Object transactionDefinition);

    /**
     * Mark this UnitOfWork as being transacted by the given transaction definition.
     * <p/>
     * The definition will most likely be a Spring TransactionTemplate when using Spring Transaction
     * <p/>
     * When the transaction is completed then invoke the {@link #endTransactedBy(Object)} method.
     *
     * @param transactionDefinition the transaction definition
     */
    void beginTransactedBy(Object transactionDefinition);

    /**
     * Mark this UnitOfWork as not transacted anymore by the given transaction definition.
     * <p/>
     * The definition will most likely be a Spring TransactionTemplate when using Spring Transaction
     *
     * @param transactionDefinition the transaction definition
     */
    void endTransactedBy(Object transactionDefinition);

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
}
