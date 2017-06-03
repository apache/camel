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

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.TypeConverterSupport;
import org.apache.camel.util.AsyncProcessorConverterHelper;

/**
 * A simple converter that can convert any {@link Processor} to an {@link AsyncProcessor}.
 * Processing will still occur synchronously but it will provide the required
 * notifications that the caller expects.
 *
 * @version
 */
public class AsyncProcessorTypeConverter extends TypeConverterSupport {

    @Override
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
        if (type.equals(AsyncProcessor.class)) {
            if (value instanceof Processor) {
                return type.cast(AsyncProcessorConverterHelper.convert((Processor) value));
            }
        }
        return null;
    }

    /**
     * @deprecated use {@link AsyncProcessorConverterHelper#convert(org.apache.camel.Processor)} instead
     */
    @Deprecated
    public static AsyncProcessor convert(Processor value) {
        return AsyncProcessorConverterHelper.convert(value);
    }
}
