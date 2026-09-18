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
package org.apache.camel.component.jgroups;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.spi.ExceptionHandler;

/**
 * {@link ExceptionHandler} capturing the exceptions handed to it, so that a test can assert what the consumer refused.
 */
public class CapturingExceptionHandler implements ExceptionHandler {

    private final List<Throwable> exceptions = new CopyOnWriteArrayList<>();

    public List<Throwable> getExceptions() {
        return exceptions;
    }

    @Override
    public void handleException(Throwable exception) {
        exceptions.add(exception);
    }

    @Override
    public void handleException(String message, Throwable exception) {
        exceptions.add(exception);
    }

    @Override
    public void handleException(String message, Exchange exchange, Throwable exception) {
        exceptions.add(exception);
    }
}
