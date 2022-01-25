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
package org.apache.camel.cdi;

import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.transaction.CdiTransactionalErrorHandlerBuilder;

/**
 * An extension of the {@link RouteBuilder} to provide some additional JTA helper methods.
 *
 * You never really need it since {@code transactionErrorHandler()} can be replaced by
 * {@code new JtaTransactionErrorHandlerBuilder()}.
 */
public abstract class CdiRouteBuilder extends RouteBuilder {

    /**
     * Creates a transaction error handler that will lookup in application context for an exiting transaction manager.
     *
     * @return the created error handler
     */
    // IMPORTANT: don't leak CdiJtaTransactionErrorHandlerBuilder in the signature,
    //            only things not depending on camel-jta
    public <T extends DefaultErrorHandlerBuilder & CdiTransactionalErrorHandlerBuilder> T transactionErrorHandler() {
        try {
            return (T) new org.apache.camel.cdi.transaction.CdiJtaTransactionErrorHandlerBuilder();
        } catch (final NoClassDefFoundError e) {
            throw new IllegalStateException("JTA not available");
        }
    }
}
