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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * A simple converter that can convert any {@link Processor} to an {@link AsyncProcessor}.
 * Processing will still occur synchronously but it will provide the required
 * notifications that the caller expects.
 *
 * @version 
 */
public final class AsyncProcessorConverterHelper {
    
    private AsyncProcessorConverterHelper() {
        // Helper class
    }

    private static final class ProcessorToAsyncProcessorBridge extends DelegateProcessor implements AsyncProcessor {

        private ProcessorToAsyncProcessorBridge(Processor processor) {
            super(processor);
        }

        public boolean process(Exchange exchange, AsyncCallback callback) {
            if (processor == null) {
                // no processor then we are done
                callback.done(true);
                return true;
            }
            try {
                processor.process(exchange);
            } catch (Throwable e) {
                // must catch throwable so we catch all
                exchange.setException(e);
            } finally {
                // we are bridging a sync processor as async so callback with true
                callback.done(true);
            }
            return true;
        }

        @Override
        public String toString() {
            if (processor != null) {
                return processor.toString();
            } else {
                return "Processor is null";
            }
        }
    }

    public static AsyncProcessor convert(Processor value) {
        if (value instanceof AsyncProcessor) {
            return (AsyncProcessor)value;
        }
        return new ProcessorToAsyncProcessorBridge(value);
    }
}
