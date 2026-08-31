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
package org.apache.camel.saga;

import java.util.concurrent.CompletableFuture;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Service;

/**
 * A Camel saga service is a factory of saga coordinators.
 */
public interface CamelSagaService extends Service, CamelContextAware {

    CompletableFuture<CamelSagaCoordinator> newSaga(Exchange exchange);

    CompletableFuture<CamelSagaCoordinator> getSaga(String id);

    void registerStep(CamelSagaStep step);

    /**
     * Whether a saga coordinator may be selected from the {@code Long-Running-Action} message header.
     * <p>
     * The saga id normally travels in the exchange's internal state, which survives {@code removeHeaders("*")}. The
     * header is consulted as well so that a coordinator started elsewhere can be joined - the LRA protocol carries the
     * id that way. That only makes sense for a service which actually participates in such a protocol: the header sits
     * outside the {@code Camel} namespace that consumers filter, and the id is written back onto responses, so where no
     * external coordinator exists it lets a message choose which saga its exchange joins.
     * <p>
     * Defaults to false. A service that takes part in a distributed saga protocol overrides it.
     *
     * @return true if the {@code Long-Running-Action} header may select a coordinator
     * @since  4.23
     */
    default boolean isLongRunningActionHeaderSupported() {
        return false;
    }

}
