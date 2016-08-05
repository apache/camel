/**
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
package org.apache.camel.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.spi.SubUnitOfWork;
import org.apache.camel.spi.SubUnitOfWorkCallback;

/**
 * A default implementation of {@link org.apache.camel.spi.SubUnitOfWork} combined
 * with a {@link SubUnitOfWorkCallback} to gather callbacks into this {@link SubUnitOfWork} state
 */
public class DefaultSubUnitOfWork implements SubUnitOfWork, SubUnitOfWorkCallback {

    private List<Exception> failedExceptions;
    private boolean failed;

    @Override
    public void onExhausted(Exchange exchange) {
        if (exchange.getException() != null) {
            addFailedException(exchange.getException());
            failed = true;
        }
    }

    @Override
    public void onDone(Exchange exchange) {
        if (exchange.getException() != null) {
            addFailedException(exchange.getException());
            failed = true;
        }
    }

    @Override
    public boolean isFailed() {
        return failed;
    }

    @Override
    public List<Exception> getExceptions() {
        return failedExceptions;
    }

    private void addFailedException(Exception exception) {
        if (failedExceptions == null) {
            failedExceptions = new ArrayList<Exception>();
        }
        if (!failedExceptions.contains(exception)) {
            // avoid adding the same exception multiple times
            failedExceptions.add(exception);
        }
    }

}
