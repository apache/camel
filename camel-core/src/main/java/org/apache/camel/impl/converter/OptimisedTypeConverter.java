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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.converter.IOConverterOptimised;
import org.apache.camel.converter.NIOConverterOptimised;
import org.apache.camel.converter.ObjectConverterOptimised;
import org.apache.camel.converter.TimePatternConverterOptimised;

/**
 * Optimised type converter for performing the most common conversions using the type converters
 * from camel-core.
 * <p/>
 * The most commonly used type converters has been optimised to be invoked in a faster by
 * using direct method calls instead of a calling via a reflection method call via
 * {@link InstanceMethodTypeConverter} or {@link StaticMethodTypeConverter}.
 * In addition the performance is faster because the type converter is not looked up
 * via a key in the type converter {@link Map}; which requires creating a new object
 * as they key and perform the map lookup. The caveat is that for any new type converter
 * to be included it must be manually added by adding the nessasary source code to the
 * optimised classes such as {@link ObjectConverterOptimised}.
 */
public class OptimisedTypeConverter {

    private final EnumTypeConverter enumTypeConverter = new EnumTypeConverter();

    /**
     * Attempts to convert the value to the given type
     *
     * @param type     the type to convert to
     * @param exchange the exchange, may be <tt>null</tt>
     * @param value    the value
     * @return the converted value, or <tt>null</tt> if no optimised core type converter exists to convert
     */
    public Object convertTo(final Class<?> type, final Exchange exchange, final Object value) throws Exception {
        Object answer;

        // use the optimised type converters and use them in the most commonly used order

        // we need time pattern first as it can do a special String -> long conversion which should happen first
        answer = TimePatternConverterOptimised.convertTo(type, exchange, value);
        if (answer == null) {
            answer = ObjectConverterOptimised.convertTo(type, exchange, value);
        }
        if (answer == null) {
            answer = IOConverterOptimised.convertTo(type, exchange, value);
        }
        if (answer == null) {
            answer = NIOConverterOptimised.convertTo(type, exchange, value);
        }

        // specially optimised for enums
        if (answer == null && type.isEnum()) {
            answer = enumTypeConverter.convertTo(type, exchange, value);
        }

        return answer;
    }

}
