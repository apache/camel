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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * The processor which sets an {@link Exception} on the {@link Exchange}
 */
public class ThrowExceptionProcessor extends ServiceSupport implements AsyncProcessor, Traceable, IdAware {
    private String id;
    private final Exception exception;

    public ThrowExceptionProcessor(Exception exception) {
        ObjectHelper.notNull(exception, "exception", this);
        this.exception = exception;
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        exchange.setException(exception);
        callback.done(true);
        return true;
    }

    public String getTraceLabel() {
        return "throwException[" + exception.getClass().getSimpleName() + "]";
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

    public String toString() {
        return "ThrowException";
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}