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

import java.util.concurrent.Future;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.StreamCache;
import org.apache.camel.TypeConverter;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Future type converter.
 *
 * @version 
 */
@Converter
public final class FutureTypeConverter implements TypeConverter {

    private static final Logger LOG = LoggerFactory.getLogger(FutureTypeConverter.class);

    private final TypeConverter converter;

    public FutureTypeConverter(TypeConverter converter) {
        this.converter = converter;
    }

    @SuppressWarnings("unchecked")
    private <T> T doConvertTo(Class<T> type, Exchange exchange, Object value) throws Exception {
        // do not convert to stream cache
        if (StreamCache.class.isAssignableFrom(value.getClass())) {
            return null;
        }

        if (Future.class.isAssignableFrom(value.getClass())) {

            Future future = (Future) value;

            if (future.isCancelled()) {
                // return void to indicate its not possible to convert at this time
                return (T) Void.TYPE;
            }

            // do some trace logging as the get is blocking until the response is ready
            LOG.trace("Getting future response");

            Object body = future.get();
            LOG.trace("Got future response");

            if (body == null) {
                // return void to indicate its not possible to convert at this time
                return (T) Void.TYPE;
            }

            // maybe from is already the type we want
            if (type.isAssignableFrom(body.getClass())) {
                return type.cast(body);
            } else if (body instanceof Exchange) {
                Exchange result = (Exchange) body;
                body = ExchangeHelper.extractResultBody(result, result.getPattern());
            }

            // no then try to lookup a type converter
            return converter.convertTo(type, exchange, body);
        }

        return null;
    }

    public <T> T convertTo(Class<T> type, Object value) {
        return convertTo(type, null, value);
    }

    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
        try {
            return doConvertTo(type, exchange, value);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    public <T> T mandatoryConvertTo(Class<T> type, Object value) throws NoTypeConversionAvailableException {
        return mandatoryConvertTo(type, null, value);
    }

    public <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value) throws NoTypeConversionAvailableException {
        T answer;
        try {
            answer = doConvertTo(type, exchange, value);
        } catch (Exception e) {
            throw new NoTypeConversionAvailableException(value, type, e);
        }

        if (answer == null) {
            throw new NoTypeConversionAvailableException(value, type);
        }

        return answer;
    }
}
