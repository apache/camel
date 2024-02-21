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
package org.apache.camel.processor;

import java.util.concurrent.ExecutorService;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.DynamicRouter;
import org.apache.camel.Processor;
import org.apache.camel.RecipientList;
import org.apache.camel.RoutingSlip;
import org.apache.camel.spi.AnnotationBasedProcessorFactory;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

@JdkService(AnnotationBasedProcessorFactory.FACTORY)
public final class DefaultAnnotationBasedProcessorFactory implements AnnotationBasedProcessorFactory {

    @Override
    public AsyncProcessor createDynamicRouter(CamelContext camelContext, DynamicRouter annotation) {
        org.apache.camel.processor.DynamicRouter dynamicRouter = new org.apache.camel.processor.DynamicRouter(camelContext);
        dynamicRouter.setDelimiter(annotation.delimiter());
        dynamicRouter.setIgnoreInvalidEndpoints(annotation.ignoreInvalidEndpoints());
        dynamicRouter.setCacheSize(annotation.cacheSize());
        return dynamicRouter;
    }

    @Override
    public AsyncProcessor createRecipientList(CamelContext camelContext, RecipientList annotation) {
        org.apache.camel.processor.RecipientList recipientList
                = new org.apache.camel.processor.RecipientList(camelContext, annotation.delimiter());
        recipientList.setStopOnException(annotation.stopOnException());
        recipientList.setIgnoreInvalidEndpoints(annotation.ignoreInvalidEndpoints());
        recipientList.setParallelProcessing(annotation.parallelProcessing());
        recipientList.setParallelAggregate(annotation.parallelAggregate());
        recipientList.setStreaming(annotation.streaming());
        recipientList.setTimeout(annotation.timeout());
        recipientList.setCacheSize(annotation.cacheSize());
        recipientList.setShareUnitOfWork(annotation.shareUnitOfWork());

        if (ObjectHelper.isNotEmpty(annotation.executorService())) {
            ExecutorService executor = camelContext.getExecutorServiceManager().newThreadPool(this, "@RecipientList",
                    annotation.executorService());
            recipientList.setExecutorService(executor);
        }

        if (annotation.parallelProcessing() && recipientList.getExecutorService() == null) {
            // we are running in parallel so we need a thread pool
            ExecutorService executor = camelContext.getExecutorServiceManager().newDefaultThreadPool(this, "@RecipientList");
            recipientList.setExecutorService(executor);
        }

        if (ObjectHelper.isNotEmpty(annotation.aggregationStrategy())) {
            AggregationStrategy strategy
                    = CamelContextHelper.mandatoryLookup(camelContext, annotation.aggregationStrategy(),
                            AggregationStrategy.class);
            recipientList.setAggregationStrategy(strategy);
        }

        if (ObjectHelper.isNotEmpty(annotation.onPrepare())) {
            Processor onPrepare = CamelContextHelper.mandatoryLookup(camelContext, annotation.onPrepare(), Processor.class);
            recipientList.setOnPrepare(onPrepare);
        }

        return recipientList;
    }

    @Override
    public AsyncProcessor createRoutingSlip(CamelContext camelContext, RoutingSlip annotation) {
        org.apache.camel.processor.RoutingSlip routingSlip = new org.apache.camel.processor.RoutingSlip(camelContext);
        routingSlip.setDelimiter(annotation.delimiter());
        routingSlip.setIgnoreInvalidEndpoints(annotation.ignoreInvalidEndpoints());
        routingSlip.setCacheSize(annotation.cacheSize());
        return routingSlip;
    }
}
