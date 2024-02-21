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
 * Factory to create {@link org.apache.camel.spi.UnitOfWork}.
 *
 * <b>IMPORTANT:</b> Implementing a custom {@link UnitOfWorkFactory} is only intended for very rare and special
 * use-cases. The created {@link UnitOfWork} is highly recommended to extend
 * org.apache.camel.impl.engine.DefaultUnitOfWork to ensure Camel functionality works correctly during routing of
 * {@link Exchange}s.
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
