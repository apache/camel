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

import java.util.concurrent.CountDownLatch;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;

/**
 * Helper methods for AsyncProcessor objects.
 */
public final class AsyncProcessorHelper {
    
    private AsyncProcessorHelper() {
        // utility class
    }

    /**
     * Calls the async version of the processor's process method and waits
     * for it to complete before returning. This can be used by AsyncProcessor
     * objects to implement their sync version of the process method.
     */
    public static void process(AsyncProcessor processor, Exchange exchange) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        boolean sync = processor.process(exchange, new AsyncCallback() {
            public void done(boolean sync) {
                if (!sync) {
                    latch.countDown();
                }
            }
        });
        if (!sync) {
            latch.await();
        }
    }
}
