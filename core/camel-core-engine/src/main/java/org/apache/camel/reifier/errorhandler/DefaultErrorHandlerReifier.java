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
package org.apache.camel.reifier.errorhandler;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.processor.errorhandler.DefaultErrorHandler;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.ThreadPoolProfile;

public class DefaultErrorHandlerReifier<T extends DefaultErrorHandlerBuilder> extends ErrorHandlerReifier<T> {

    public DefaultErrorHandlerReifier(Route route, ErrorHandlerFactory definition) {
        super(route, (T)definition);
    }

    @Override
    public Processor createErrorHandler(Processor processor) throws Exception {
        DefaultErrorHandler answer = new DefaultErrorHandler(camelContext, processor, definition.getLogger(), definition.getOnRedelivery(),
                                                             definition.getRedeliveryPolicy(), definition.getExceptionPolicyStrategy(),
                                                             definition.getRetryWhilePolicy(camelContext), getExecutorService(camelContext),
                                                             definition.getOnPrepareFailure(), definition.getOnExceptionOccurred());
        // configure error handler before we can use it
        configure(answer);
        return answer;
    }

    protected synchronized ScheduledExecutorService getExecutorService(CamelContext camelContext) {
        ScheduledExecutorService executorService = definition.getExecutorService();
        String executorServiceRef = definition.getExecutorServiceRef();
        if (executorService == null || executorService.isShutdown()) {
            // camel context will shutdown the executor when it shutdown so no
            // need to shut it down when stopping
            if (executorServiceRef != null) {
                executorService = lookup(executorServiceRef, ScheduledExecutorService.class);
                if (executorService == null) {
                    ExecutorServiceManager manager = camelContext.getExecutorServiceManager();
                    ThreadPoolProfile profile = manager.getThreadPoolProfile(executorServiceRef);
                    executorService = manager.newScheduledThreadPool(this, executorServiceRef, profile);
                }
                if (executorService == null) {
                    throw new IllegalArgumentException("ExecutorServiceRef " + executorServiceRef + " not found in registry.");
                }
            } else {
                // no explicit configured thread pool, so leave it up to the
                // error handler to decide if it need
                // a default thread pool from
                // CamelContext#getErrorHandlerExecutorService
                executorService = null;
            }
            // TODO: ErrorHandler: no modification to the model should be done
            definition.setExecutorService(executorService);
        }
        return executorService;
    }

}
