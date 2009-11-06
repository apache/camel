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
package org.apache.camel.component.jetty;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;

/**
 * A {@link Future} task which to be used to retrieve the HTTP response which comes back asynchronously.
 *
 * @version $Revision$
 */
public class JettyFutureGetBody implements Future<String>, Serializable {

    private final Exchange exchange;
    private final JettyContentExchange httpExchange;
    private boolean cancelled;
    private boolean throwExceptionOnFailure;

    public JettyFutureGetBody(Exchange exchange, JettyContentExchange httpExchange, boolean throwExceptionOnFailure) {
        this.exchange = exchange;
        this.httpExchange = httpExchange;
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public boolean cancel(boolean mayInterrupt) {
        httpExchange.cancel();
        cancelled = true;
        return true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean isDone() {
        return httpExchange.isBodyComplete();
    }

    public String get() throws InterruptedException, ExecutionException {
        // wait for body to be done
        if (!isDone()) {
            try {
                httpExchange.waitForBodyToComplete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // ignore
            }
        }
        return doGetBody();
    }

    public String get(long timeout, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
        if (!isDone()) {
            boolean done = httpExchange.waitForBodyToComplete(timeout, timeUnit);
            if (done) {
                return doGetBody();
            } else {
                ExchangeTimedOutException cause = new ExchangeTimedOutException(exchange, timeout);
                throw new ExecutionException(cause);
            }
        }
        return doGetBody();
    }

    private String doGetBody() throws ExecutionException {
        try {
            if (httpExchange.isFailed() && throwExceptionOnFailure) {
                throw new JettyHttpOperationFailedException(httpExchange.getUrl(), httpExchange.getResponseStatus(), httpExchange.getBody());
            } else {
                return httpExchange.getBody();
            }
        } catch (UnsupportedEncodingException e) {
            throw new ExecutionException(e);
        } catch (JettyHttpOperationFailedException e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public String toString() {
        return "JettyFutureGetBody[" + httpExchange.getUrl() + "]";
    }

}
