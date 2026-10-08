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
package org.apache.camel.component.docling;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledPollConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event-driven consumer for docling async conversions.
 * <p>
 * It polls the component-wide registry of tasks submitted via {@code SUBMIT_ASYNC_CONVERSION} and, each time a task
 * completes, emits one exchange whose body is the converted content (the same shape the synchronous {@code CONVERT_*}
 * operations produce for the configured {@code outputFormat}), carrying the task id in the
 * {@link DoclingHeaders#TASK_ID} header. A task that failed completes the exchange with the underlying exception, so
 * the route's {@code onException} machinery applies. The poll interval is the standard scheduled-consumer {@code delay}
 * option.
 */
public class DoclingConsumer extends ScheduledPollConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DoclingConsumer.class);

    public DoclingConsumer(DoclingEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public DoclingEndpoint getEndpoint() {
        return (DoclingEndpoint) super.getEndpoint();
    }

    @Override
    protected int poll() throws Exception {
        Map<String, AsyncTaskEntry> pendingAsyncTasks
                = ((DoclingComponent) getEndpoint().getComponent()).getPendingAsyncTasks();

        int polled = 0;
        for (Map.Entry<String, AsyncTaskEntry> entry : pendingAsyncTasks.entrySet()) {
            String taskId = entry.getKey();
            AsyncTaskEntry task = entry.getValue();
            if (!task.getFuture().isDone()) {
                continue;
            }
            // claim the completed task atomically, so a concurrent CHECK_CONVERSION_STATUS or another consumer cannot
            // emit the same completion twice
            if (!pendingAsyncTasks.remove(taskId, task)) {
                continue;
            }

            polled++;
            Exchange exchange = createExchange(false);
            try {
                exchange.getIn().setHeader(DoclingHeaders.TASK_ID, taskId);
                emitResult(exchange, task);
                getProcessor().process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            } finally {
                if (exchange.getException() != null) {
                    getExceptionHandler().handleException(
                            "Error processing docling async task " + taskId, exchange, exchange.getException());
                }
                releaseExchange(exchange, false);
            }
        }
        return polled;
    }

    private void emitResult(Exchange exchange, AsyncTaskEntry task) {
        String taskId = task.getTaskId();
        try {
            ConvertDocumentResponse response = task.getFuture().join();
            // extract with the format the task was submitted with, not this consuming endpoint's: docling-serve only
            // returns the requested format, so using the consumer's own outputFormat would yield an empty body
            exchange.getIn().setBody(DoclingContentExtractor.extract(response, task.getOutputFormat()));
            LOG.debug("Emitting completed docling async task {}", taskId);
        } catch (CancellationException | CompletionException e) {
            // the conversion failed (or was evicted); surface the underlying cause on the exchange
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            LOG.debug("Docling async task {} failed: {}", taskId, cause.getMessage());
            exchange.setException(cause);
        } catch (Exception e) {
            exchange.setException(e);
        }
    }
}
