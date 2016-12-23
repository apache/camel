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
package org.apache.camel.spring.boot;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.support.TypeConverterSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.convert.TypeDescriptor;

public class SpringTypeConverter extends TypeConverterSupport {

    private final List<ConversionService> conversionServices;
    private final ConcurrentHashMap<Class<?>, TypeDescriptor> types;

    @Autowired
    public SpringTypeConverter(List<ConversionService> conversionServices) {
        this.conversionServices = conversionServices;
        this.types = new ConcurrentHashMap<>();
    }

    @Override
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
        // do not attempt to convert Camel types
        if (type.getCanonicalName().startsWith("org.apache")) {
            return null;
        }
        
        // do not attempt to convert List -> Map. Ognl expression may use this converter as a fallback expecting null
        if (type.isAssignableFrom(Map.class) && (value.getClass().isArray() || value instanceof Collection)) {
            return null;
        }

        TypeDescriptor sourceType = types.computeIfAbsent(value.getClass(), TypeDescriptor::valueOf);
        TypeDescriptor targetType = types.computeIfAbsent(type, TypeDescriptor::valueOf);

        for (ConversionService conversionService : conversionServices) {
            if (conversionService.canConvert(sourceType, targetType)) {
                try {
                    return (T)conversionService.convert(value, sourceType, targetType);
                } catch (ConversionFailedException e) {
                    // if value is a collection or an array the check ConversionService::canConvert
                    // may return true but then the conversion of specific objects may fail
                    //
                    // https://issues.apache.org/jira/browse/CAMEL-10548
                    // https://jira.spring.io/browse/SPR-14971
                    //
                    if (e.getCause() instanceof ConverterNotFoundException && isArrayOrCollection(value)) {
                        return null;
                    } else {
                        throw new TypeConversionException(value, type, e);
                    }
                }
            }
        }

        return null;
    }

    private boolean isArrayOrCollection(Object value) {
        return value instanceof Collection || value.getClass().isArray();
    }
}
