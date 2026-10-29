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
package org.apache.camel.component.plc4x;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.spi.ExceptionHandler;

/**
 * Collects the failures a consumer reports to its exception handler, and whether the thread that reported each one was
 * interrupted.
 */
final class PollFailures implements ExceptionHandler {

    private final List<Failure> failures = new CopyOnWriteArrayList<>();

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
        failures.add(new Failure(exception, Thread.currentThread().isInterrupted()));
    }

    List<Failure> failures() {
        return failures;
    }

    int count() {
        return failures.size();
    }

    List<Throwable> causes() {
        return failures.stream().map(Failure::cause).toList();
    }

    record Failure(Throwable cause, boolean interrupted) {
    }
}
