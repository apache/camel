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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.TypeConversionException;
import org.apache.camel.support.TypeConverterSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.ConversionService;

public class SpringTypeConverter extends TypeConverterSupport {

    private final List<ConversionService> conversionServices;

    @Autowired
    public SpringTypeConverter(List<ConversionService> conversionServices) {
        this.conversionServices = conversionServices;
    }

    @Override
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
        // do not attempt to convert Camel types
        if (type.getCanonicalName().startsWith("org.apache")) {
            return null;
        }

        for (ConversionService conversionService : conversionServices) {
            if (conversionService.canConvert(value.getClass(), type)) {
                return conversionService.convert(value, type);
            }
        }
        return null;
    }

}
