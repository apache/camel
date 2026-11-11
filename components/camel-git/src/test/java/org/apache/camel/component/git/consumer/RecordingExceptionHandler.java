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
package org.apache.camel.component.git.consumer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.spi.ExceptionHandler;

/**
 * Records what a consumer reports to its exception handler. The body of the exchange is read when the failure is
 * reported, as the consumer may release the exchange afterwards.
 */
class RecordingExceptionHandler implements ExceptionHandler {

    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<Throwable> exceptions = new CopyOnWriteArrayList<>();

    @Override
    public void handleException(Throwable exception) {
        handleException(null, null, exception);
    }

    @Override
    public void handleException(String message, Throwable exception) {
        handleException(message, null, exception);
    }

    @Override
    public void handleException(String message, Exchange exchange, Throwable exception) {
        bodies.add(exchange != null ? exchange.getMessage().getBody(String.class) : null);
        exceptions.add(exception);
    }

    List<String> getBodies() {
        return bodies;
    }

    List<Throwable> getExceptions() {
        return exceptions;
    }
}
