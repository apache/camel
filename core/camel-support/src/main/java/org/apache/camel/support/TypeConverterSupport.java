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

import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;

/**
 * Base class for {@link TypeConverter} implementations.
 * <p/>
 * Implementators need only to implement the {@link TypeConverter#convertTo(Class, org.apache.camel.Exchange, Object)}
 * method, and can rely on the default implementations of the other methods from this support class.
 */
public abstract class TypeConverterSupport implements TypeConverter {

    @Override
    public boolean allowNull() {
        return false;
    }

    @Override
    public <T> T convertTo(Class<T> type, Object value) throws TypeConversionException {
        return convertTo(type, null, value);
    }

    @Override
    public <T> T mandatoryConvertTo(Class<T> type, Object value) throws TypeConversionException, NoTypeConversionAvailableException {
        T t = convertTo(type, null, value);
        if (t == null) {
            throw new NoTypeConversionAvailableException(value, type);
        } else {
            return t;
        }
    }

    @Override
    public <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException, NoTypeConversionAvailableException {
        T t = convertTo(type, exchange, value);
        if (t == null) {
            throw new NoTypeConversionAvailableException(value, type);
        } else {
            return t;
        }
    }

    @Override
    public <T> T tryConvertTo(Class<T> type, Object value) {
        try {
            return convertTo(type, null, value);
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    @Override
    public <T> T tryConvertTo(Class<T> type, Exchange exchange, Object value) {
        try {
            return convertTo(type, exchange, value);
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
