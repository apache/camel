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
package org.apache.camel.mdc;

import java.util.concurrent.CompletableFuture;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.AsyncProcessorConverterHelper;

/**
 * MDCProcessorsInterceptStrategy is used to wrap each processor calls and generate the MDC context for each process
 * execution.
 */
public class MDCProcessorsInterceptStrategy implements InterceptStrategy {

    private MDCService mdcService;

    public MDCProcessorsInterceptStrategy(MDCService mdcService) {
        this.mdcService = mdcService;
    }

    @Override
    public Processor wrapProcessorInInterceptors(
            final CamelContext context,
            final NamedNode definition,
            final Processor target,
            final Processor nextTarget)
            throws Exception {

        final AsyncProcessor asyncProcessor = AsyncProcessorConverterHelper.convert(target);

        return new AsyncProcessor() {

            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                mdcService.setMDC(exchange);
                return asyncProcessor.process(exchange, doneSync -> {
                    mdcService.unsetMDC();
                    callback.done(doneSync);
                });
            }

            @Override
            public void process(Exchange exchange) throws Exception {
                mdcService.setMDC(exchange);
                try {
                    asyncProcessor.process(exchange);
                } finally {
                    mdcService.unsetMDC();
                }
            }

            @Override
            public CompletableFuture<Exchange> processAsync(Exchange exchange) {
                CompletableFuture<Exchange> future = new CompletableFuture<>();
                mdcService.setMDC(exchange);
                asyncProcessor.process(exchange, doneSync -> {
                    if (exchange.getException() != null) {
                        future.completeExceptionally(exchange.getException());
                    } else {
                        future.complete(exchange);
                    }
                    mdcService.unsetMDC();
                });

                return future;
            }
        };
    }

}
