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

import org.apache.camel.AfterPropertiesConfigured;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;

/**
 * SPI factory that creates a {@link UnitOfWork} for each incoming {@link org.apache.camel.Exchange}.
 * <p/>
 * The factory is called by the routing engine at exchange creation time; the returned {@link UnitOfWork} is attached to
 * the exchange for its lifetime. Implementing a custom factory is only necessary for very rare use-cases such as
 * integrating with an external transaction manager or attaching custom synchronization hooks globally. The created
 * {@link UnitOfWork} <b>must</b> extend {@code DefaultUnitOfWork} from {@code camel-base-engine}: that class wires the
 * breadcrumb ID, MDC propagation, and route-context tracking that the routing engine relies on. Implementing
 * {@link UnitOfWork} from scratch without extending {@code DefaultUnitOfWork} will break async routing and MDC logging
 * in subtle ways.
 * <p/>
 * See <a href="https://camel.apache.org/manual/exchange.html">Exchange</a> in the Camel user manual.
 *
 * @see UnitOfWork
 */
public interface UnitOfWorkFactory extends AfterPropertiesConfigured {

    /**
     * Creates a new {@link UnitOfWork}
     *
     * @param  exchange the exchange
     * @return          the created {@link UnitOfWork}
     */
    UnitOfWork createUnitOfWork(Exchange exchange);

    @Override
    default void afterPropertiesConfigured(CamelContext camelContext) {
        // noop
    }

}
