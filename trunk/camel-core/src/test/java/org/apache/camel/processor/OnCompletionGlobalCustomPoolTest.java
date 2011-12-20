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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class OnCompletionGlobalCustomPoolTest extends OnCompletionGlobalTest {

    private ExecutorService pool; 
    
    protected void setUp() throws Exception {
        // use a pool with 2 concurrent tasks so we cannot run too fast
        pool = Executors.newFixedThreadPool(2);
        super.setUp();
    }
    
    protected void tearDown() throws Exception {
        super.tearDown();
        pool.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                // use a custom thread pool
                onCompletion().executorService(pool).to("log:global").to("mock:sync");

                from("direct:start")
                    .process(new MyProcessor())
                    .to("mock:result");
            }
        };
    }

}