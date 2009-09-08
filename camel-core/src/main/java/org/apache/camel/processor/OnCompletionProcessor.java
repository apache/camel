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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.impl.SynchronizationAdapter;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public class OnCompletionProcessor extends ServiceSupport implements Processor, Traceable {

    private static final int DEFAULT_THREADPOOL_SIZE = 10;
    private static final transient Log LOG = LogFactory.getLog(OnCompletionProcessor.class);
    private ExecutorService executorService;
    private Processor processor;
    private boolean onCompleteOnly;
    private boolean onFailureOnly;
    private Predicate onWhen;

    public OnCompletionProcessor(Processor processor, boolean onCompleteOnly, boolean onFailureOnly, Predicate onWhen) {
        this.processor = processor;
        this.onCompleteOnly = onCompleteOnly;
        this.onFailureOnly = onFailureOnly;
        this.onWhen = onWhen;
    }

    protected void doStart() throws Exception {
        ServiceHelper.startService(processor);
    }

    @Override
    protected void doStop() throws Exception {
        if (executorService != null) {
            executorService.shutdown();
            // must null it so we can restart
            executorService = null;
        }
        ServiceHelper.stopService(processor);
    }

    public void process(Exchange exchange) throws Exception {
        if (processor == null) {
            return;
        }

        // register callback
        exchange.getUnitOfWork().addSynchronization(new SynchronizationAdapter() {
            @Override
            public void onComplete(Exchange exchange) {
                if (onFailureOnly) {
                    return;
                }

                if (onWhen != null && !onWhen.matches(exchange)) {
                    // predicate did not match so do not route the onComplete
                    return;
                }

                // must use a copy as we dont want it to cause side effects of the original exchange
                final Exchange copy = prepareExchange(exchange);

                getExecutorService().submit(new Callable<Exchange>() {
                    public Exchange call() throws Exception {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Processing onComplete: " + copy);
                        }
                        processor.process(copy);
                        return copy;
                    }
                });
            }

            public void onFailure(Exchange exchange) {
                if (onCompleteOnly) {
                    return;
                }

                if (onWhen != null && !onWhen.matches(exchange)) {
                    // predicate did not match so do not route the onComplete
                    return;
                }

                // must use a copy as we dont want it to cause side effects of the original exchange
                final Exchange copy = prepareExchange(exchange);
                // must remove exception otherwise onFailure routing will fail as well
                // the caused exception is stored as a property (Exchange.EXCEPTION_CAUGHT) on the exchange
                copy.setException(null);

                getExecutorService().submit(new Callable<Exchange>() {
                    public Exchange call() throws Exception {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Processing onFailure: " + copy);
                        }

                        processor.process(copy);
                        return copy;
                    }
                });
            }

            @Override
            public String toString() {
                if (!onCompleteOnly && !onFailureOnly) {
                    return "onCompleteOrFailure";
                } else if (onCompleteOnly) {
                    return "onCompleteOnly";
                } else {
                    return "onFailureOnly";
                }
            }
        });
    }

    /**
     * Prepares the {@link Exchange} to send as onCompletion.
     *
     * @param exchange the current exchange
     * @return the exchange to be routed in onComplete
     */
    protected Exchange prepareExchange(Exchange exchange) {
        // must use a copy as we dont want it to cause side effects of the original exchange
        final Exchange copy = ExchangeHelper.createCorrelatedCopy(exchange, false);
        // set MEP to InOnly as this wire tap is a fire and forget
        copy.setPattern(ExchangePattern.InOnly);
        // add a header flag to indicate its a on completion exchange
        copy.setProperty(Exchange.ON_COMPLETION, Boolean.TRUE);
        return copy;
    }

    public ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = createExecutorService();
        }
        return executorService;
    }

    private ExecutorService createExecutorService() {
        return ExecutorServiceHelper.newScheduledThreadPool(DEFAULT_THREADPOOL_SIZE, this.toString(), true);
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public String toString() {
        return "OnCompletionProcessor[" + processor + "]";
    }

    public String getTraceLabel() {
        return "OnCompletion";
    }
}
