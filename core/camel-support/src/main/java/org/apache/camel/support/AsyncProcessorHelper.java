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
package org.apache.camel.support;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.AsyncProcessorAwaitManager;

/**
 * Helper methods for {@link AsyncProcessor} objects.
 */
public final class AsyncProcessorHelper {

    private AsyncProcessorHelper() {
        // utility class
    }

    /**
     * Calls the async version of the processor's process method and waits
     * for it to complete before returning. This can be used by {@link AsyncProcessor}
     * objects to implement their sync version of the process method.
     * <p/>
     * <b>Important:</b> This method is discouraged to be used, as its better to invoke the asynchronous
     * {@link AsyncProcessor#process(org.apache.camel.Exchange, org.apache.camel.AsyncCallback)} method, whenever possible.
     *
     * @param processor the processor
     * @param exchange  the exchange
     * @throws Exception can be thrown if waiting is interrupted
     */
    public static void process(final AsyncProcessor processor, final Exchange exchange) throws Exception {
        final AsyncProcessorAwaitManager awaitManager = exchange.getContext().adapt(ExtendedCamelContext.class).getAsyncProcessorAwaitManager();
        awaitManager.process(processor, exchange);
    }

}
