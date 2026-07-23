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

import java.util.Objects;

/**
 * Thrown (or explicitly constructed and set on an {@link Exchange}) to signal that the current exchange should be
 * rolled back by the error handler.
 * <p/>
 * When a route uses the {@code rollback()} DSL step or a {@link Processor} throws this exception, the Camel error
 * handler treats it as an intentional rollback rather than an unexpected failure. Transactional resources enlisted in
 * the exchange's unit of work will be rolled back accordingly.
 *
 * @see Exchange
 */
public class RollbackExchangeException extends CamelExchangeException {

    /**
     * @param exchange the exchange that is being rolled back
     */
    public RollbackExchangeException(Exchange exchange) {
        this("Intended rollback", exchange);
    }

    /**
     * @param exchange the exchange that is being rolled back
     * @param cause    the cause of the rollback
     */
    public RollbackExchangeException(Exchange exchange, Throwable cause) {
        this("Intended rollback", exchange, cause);
    }

    /**
     * @param message  the detail message
     * @param exchange the exchange that is being rolled back
     */
    public RollbackExchangeException(String message, Exchange exchange) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(exchange, "exchange"));
    }

    /**
     * @param message  the detail message
     * @param exchange the exchange that is being rolled back
     * @param cause    the cause of the rollback
     */
    public RollbackExchangeException(String message, Exchange exchange, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(exchange, "exchange"),
              Objects.requireNonNull(cause, "cause"));
    }
}
