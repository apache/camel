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

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SplitDefinition;
import org.junit.Test;

public class SplitterWithCustomThreadPoolExecutorTest extends ContextTestSupport {

    protected ThreadPoolExecutor customThreadPoolExecutor = new ThreadPoolExecutor(8, 16, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());

    @Test
    public void testSplitterWithCustomThreadPoolExecutor() throws Exception {
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor)getSplitter().getExecutorService();
        if (threadPoolExecutor == null) {
            threadPoolExecutor = context.getRegistry().lookupByNameAndType(getSplitter().getExecutorServiceRef(), ThreadPoolExecutor.class);
        }
        // this should be sufficient as core pool size is the only thing I
        // changed from the default
        assertTrue(threadPoolExecutor.getCorePoolSize() == getThreadPoolExecutor().getCorePoolSize());
        assertTrue(threadPoolExecutor.getMaximumPoolSize() == getThreadPoolExecutor().getMaximumPoolSize());
    }

    protected ThreadPoolExecutor getThreadPoolExecutor() {
        return customThreadPoolExecutor;
    }

    protected SplitDefinition getSplitter() {
        SplitDefinition result = null;
        List<RouteDefinition> routeDefinitions = context.getRouteDefinitions();
        for (RouteDefinition routeType : routeDefinitions) {
            result = firstSplitterType(routeType.getOutputs());
            if (result != null) {
                break;
            }
        }
        return result;
    }

    protected SplitDefinition firstSplitterType(List<ProcessorDefinition<?>> outputs) {
        SplitDefinition result = null;

        for (ProcessorDefinition<?> processorType : outputs) {
            if (processorType instanceof SplitDefinition) {
                result = (SplitDefinition)processorType;
            } else {
                result = firstSplitterType(processorType.getOutputs());
            }
            if (result != null) {
                break;
            }
        }
        return result;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:parallel-custom-pool").split(body().tokenize(",")).parallelProcessing().executorService(customThreadPoolExecutor).to("mock:result");
            }
        };
    }
}
