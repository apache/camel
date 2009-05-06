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
package org.apache.camel.util;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Helper methods for AsyncProcessor objects.
 */
public final class AsyncProcessorHelper {
    
    private AsyncProcessorHelper() {
        // utility class
    }

    /**
     * Processes the exchange async.
     *
     * @param executor  executor service
     * @param processor the processor
     * @param exchange  the exchange
     * @return a future handle for the task being executed asynchronously
     */
    public static Future<Exchange> asyncProcess(final ExecutorService executor, final Processor processor, final Exchange exchange) {
        Callable<Exchange> task = new Callable<Exchange>() {
            public Exchange call() throws Exception {
                processor.process(exchange);
                return exchange;
            }
        };

        return executor.submit(task);
    }
}
