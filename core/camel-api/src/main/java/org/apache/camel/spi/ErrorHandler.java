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

import org.apache.camel.Processor;

/**
 * Marker interface identifying a {@link org.apache.camel.Processor} as an error-handling wrapper, as described in the
 * <a href="https://camel.apache.org/manual/error-handler.html">Error Handler</a> documentation.
 * <p/>
 * Camel wraps each route's processing pipeline in an {@code ErrorHandler} at startup. The handler intercepts exceptions
 * thrown during message processing and applies the configured error-handling strategy, such as retrying with back-off
 * ({@code RedeliveryErrorHandler}), sending to a dead letter channel, or simply logging and continuing.
 * <p/>
 * All built-in error handler implementations (NoErrorHandler, DefaultErrorHandler, DeadLetterChannel,
 * TransactionErrorHandler) implement this interface, making it possible for Camel internals to detect whether a
 * {@link org.apache.camel.Processor} is an error handler without needing to know the concrete type.
 *
 * @see   ExceptionHandler
 * @since 3.7
 */
public interface ErrorHandler extends Processor {
}
