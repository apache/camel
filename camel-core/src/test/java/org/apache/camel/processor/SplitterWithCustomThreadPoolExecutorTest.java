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

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.model.RouteType;
import org.apache.camel.model.SplitterType;

public class SplitterWithCustomThreadPoolExecutorTest extends ContextTestSupport {

    protected ThreadPoolExecutor customThreadPoolExecutor = new ThreadPoolExecutor(8, 16, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue());

    public void testSplitterWithCustomThreadPoolExecutor() throws Exception {
        ThreadPoolExecutor threadPoolExecutor = getSplitter().getThreadPoolExecutor();
        // this should be sufficient as core pool size is the only thing I changed from the default
        assertTrue(threadPoolExecutor.getCorePoolSize() == customThreadPoolExecutor.getCorePoolSize());
        assertTrue(threadPoolExecutor.getMaximumPoolSize() == customThreadPoolExecutor.getMaximumPoolSize());
    }
    
    protected SplitterType getSplitter() {
        SplitterType result = null;
        List<RouteType> routeDefinitions = context.getRouteDefinitions();          
        for (RouteType routeType : routeDefinitions) {
            result = firstSplitterType(routeType.getOutputs());
            if (result != null) {
                break;
            }
        }
        return result;
    }    
    
    protected SplitterType firstSplitterType(List<ProcessorType<?>> outputs) {
        SplitterType result = null;
        
        for (ProcessorType processorType : outputs) {
            if (processorType instanceof SplitterType) {
                result = (SplitterType) processorType;
            } else {
                result = firstSplitterType(processorType.getOutputs());
            }
            if (result != null) {
                break;
            }
        }        
        return result;
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:parallel-custom-pool").splitter(body().tokenize(","), true, customThreadPoolExecutor).to("mock:result");
            }
        };
    }
}
