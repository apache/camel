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
package org.apache.camel.impl.converter;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.TypeConverter;
import org.apache.camel.processor.DelegateProcessor;

/**
 * A simple converter that can convert any Processor to an AsyncProcessor.
 * Processing will still occur synchronously but it will provide the required
 * notifications that the caller expects.
 * 
 * @version $Revision$
 */
public class AsyncProcessorTypeConverter implements TypeConverter {

    public static final class ProcessorToAsynProcessorBridge extends DelegateProcessor implements AsyncProcessor {

        private ProcessorToAsynProcessorBridge(Processor processor) {
            super(processor);
        }

        public boolean process(Exchange exchange, AsyncCallback callback) {
            try {
                processor.process(exchange);
            } catch (Throwable e) {
                exchange.setException(e);
            }
            // false means processing of the exchange asynchronously,
            callback.done(true);
            return true;
        }
    }

    public <T> T convertTo(Class<T> toType, Object value) {
        if (value != null) {
            if (toType.equals(AsyncProcessor.class)) {
                if (value instanceof AsyncProcessor) {
                    return toType.cast(value);
                } else if (value instanceof Processor) {
                    // Provide an async bridge to the regular processor.
                    final Processor processor = (Processor)value;
                    return toType.cast(new ProcessorToAsynProcessorBridge(processor));
                }
            }
        }
        return null;
    }

    public static AsyncProcessor convert(Processor value) {
        if (value instanceof AsyncProcessor) {
            return (AsyncProcessor)value;
        }
        return new ProcessorToAsynProcessorBridge(value);
    }
}
