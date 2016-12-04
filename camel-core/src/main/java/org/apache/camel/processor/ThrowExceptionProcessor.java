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
package org.apache.camel.processor;

import java.lang.reflect.Constructor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * The processor which sets an {@link Exception} on the {@link Exchange}
 */
public class ThrowExceptionProcessor extends ServiceSupport implements AsyncProcessor, Traceable, IdAware, CamelContextAware {
    private String id;
    private CamelContext camelContext;
    private Expression simple;
    private final Exception exception;
    private final Class<? extends Exception> type;
    private final String message;

    public ThrowExceptionProcessor(Exception exception) {
        this(exception, null, null);
    }

    public ThrowExceptionProcessor(Exception exception, Class<? extends Exception> type, String message) {
        this.exception = exception;
        this.type = type;
        this.message = message;
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        Exception cause = exception;

        try {
            if (message != null && type != null) {
                // create the message using simple language so it can be dynamic
                String text = simple.evaluate(exchange, String.class);
                // create a new exception of that type, and provide the message as
                Constructor<?> constructor = type.getDeclaredConstructor(String.class);
                cause = (Exception) constructor.newInstance(text);
                exchange.setException(cause);
            } else {
                exchange.setException(cause);
            }
        } catch (Throwable e) {
            exchange.setException(new CamelExchangeException("Error creating new instance of " + exception.getClass(), exchange, e));
        }

        callback.done(true);
        return true;
    }

    public String getTraceLabel() {
        String className = this.exception == null ? this.type.getSimpleName() : this.exception.getClass().getSimpleName();
        return "throwException[" + className + "]";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Exception getException() {
        return exception;
    }

    public Class<? extends Exception> getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String toString() {
        return "ThrowException";
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "camelContext", this);

        if (message != null) {
            simple = camelContext.resolveLanguage("simple").createExpression(message);
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
