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
package org.apache.camel.component.undertow;

import java.net.URI;

import io.undertow.server.HttpHandler;
import org.apache.camel.component.undertow.handlers.CamelWebSocketHandler;

/**
 * An undertow host abstraction
 *
 */
public interface UndertowHost {

    /**
     * Validate whether this host can process the given URI
     */
    void validateEndpointURI(URI httpURI);

    /**
     * Register a handler with the given {@link HttpHandlerRegistrationInfo}. Note that for some kinds of handlers (most
     * notably {@link CamelWebSocketHandler}), it is legal to call this method multiple times with equal
     * {@link HttpHandlerRegistrationInfo} and {@link HttpHandler}. In such cases the returned {@link HttpHandler} may
     * differ from the passed {@link HttpHandler} and the returned instance is the effectively registered one for the
     * given {@link HttpHandlerRegistrationInfo}.
     *
     * @param registrationInfo
     *            the {@link HttpHandlerRegistrationInfo} related to {@code handler}
     * @param handler
     *            the {@link HttpHandler} to register
     * @return the given {@code handler} or a different {@link HttpHandler} that has been registered with the given
     *         {@link HttpHandlerRegistrationInfo} earlier.
     */
    HttpHandler registerHandler(UndertowConsumer consumer, HttpHandlerRegistrationInfo registrationInfo, HttpHandler handler);

    /**
     * Unregister a handler with the given {@link HttpHandlerRegistrationInfo}. Note that if
     * {@link #registerHandler(HttpHandlerRegistrationInfo, HttpHandler)} was successfully invoked multiple times for an
     * equivalent {@link HttpHandlerRegistrationInfo} then {@link #unregisterHandler(HttpHandlerRegistrationInfo)} must
     * be called the same number of times to unregister the associated handler completely.
     */
    void unregisterHandler(UndertowConsumer consumer, HttpHandlerRegistrationInfo registrationInfo);

}
