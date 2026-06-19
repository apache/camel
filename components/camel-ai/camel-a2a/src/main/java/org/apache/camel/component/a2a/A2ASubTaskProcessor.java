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
package org.apache.camel.component.a2a;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.processor.BaseDelegateProcessorSupport;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.StepIdAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emits A2A progress around a scoped child processor.
 */
public class A2ASubTaskProcessor extends BaseDelegateProcessorSupport
        implements Traceable, IdAware, RouteIdAware, StepIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(A2ASubTaskProcessor.class);

    private final Expression emitBefore;
    private final Expression emitAfter;
    private final Expression emitOnError;
    private final boolean failIfNoTaskContext;
    private String id;
    private String routeId;
    private String stepId;

    public A2ASubTaskProcessor(Processor processor, Expression emitBefore, Expression emitAfter, Expression emitOnError,
                               boolean failIfNoTaskContext) {
        super(processor);
        this.emitBefore = emitBefore;
        this.emitAfter = emitAfter;
        this.emitOnError = emitOnError;
        this.failIfNoTaskContext = failIfNoTaskContext;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (missingRequiredTaskContext(exchange)) {
            fail(exchange, callback, new IllegalStateException(
                    "a2aSubTask requires an active A2A task context but none was found"));
            return true;
        }

        if (!emitBefore(exchange, callback)) {
            return true;
        }

        return processChild(exchange, callback);
    }

    private boolean missingRequiredTaskContext(Exchange exchange) {
        return failIfNoTaskContext && !A2AProgress.hasTaskContext(exchange);
    }

    private boolean emitBefore(Exchange exchange, AsyncCallback callback) {
        try {
            emitProgress(exchange, emitBefore);
            return true;
        } catch (Exception e) {
            fail(exchange, callback, e);
            return false;
        }
    }

    private boolean processChild(Exchange exchange, AsyncCallback callback) {
        try {
            return processor.process(exchange, doneSync -> {
                emitCompletionProgress(exchange);
                callback.done(doneSync);
            });
        } catch (Exception e) {
            exchange.setException(e);
            emitCompletionProgress(exchange);
            callback.done(true);
            return true;
        }
    }

    private static void fail(Exchange exchange, AsyncCallback callback, Exception e) {
        exchange.setException(e);
        callback.done(true);
    }

    private void emitCompletionProgress(Exchange exchange) {
        if (exchange.getException() == null) {
            try {
                emitProgress(exchange, emitAfter);
            } catch (Exception e) {
                exchange.setException(e);
            }
        } else {
            emitOnErrorPreservingOriginal(exchange);
        }
    }

    private void emitOnErrorPreservingOriginal(Exchange exchange) {
        Exception original = exchange.getException();
        try {
            emitProgress(exchange, emitOnError);
        } catch (Exception e) {
            original.addSuppressed(e);
            exchange.setException(original);
        }
    }

    private static void emitProgress(Exchange exchange, Expression expression) {
        if (expression != null) {
            String message = expression.evaluate(exchange, String.class);
            try {
                A2AProgress.emit(exchange, message);
            } catch (RuntimeException e) {
                LOG.debug("A2A progress update failed: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public String getTraceLabel() {
        return "a2aSubTask";
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    @Override
    public String getStepId() {
        return stepId;
    }

    @Override
    public void setStepId(String stepId) {
        this.stepId = stepId;
    }
}
