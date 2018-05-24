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
package org.apache.camel.spring.boot.util;

import java.util.List;

import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;

public class CompositeConversionService implements ConversionService {
    private final List<ConversionService> delegates;

    public CompositeConversionService(List<ConversionService> delegates) {
        this.delegates = delegates;
    }

    @Override
    public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
        for (ConversionService service : this.delegates) {
            if (service.canConvert(sourceType, targetType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canConvert(TypeDescriptor sourceType, TypeDescriptor targetType) {
        for (ConversionService service : this.delegates) {
            if (service.canConvert(sourceType, targetType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public <T> T convert(Object source, Class<T> targetType) {
        for (int i = 0; i < this.delegates.size() - 1; i++) {
            try {
                ConversionService delegate = this.delegates.get(i);
                if (delegate.canConvert(source.getClass(), targetType)) {
                    return delegate.convert(source, targetType);
                }
            } catch (ConversionException e) {
                // ignored
            }
        }

        return this.delegates.get(this.delegates.size() - 1).convert(source, targetType);
    }

    @Override
    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        for (int i = 0; i < this.delegates.size() - 1; i++) {
            try {
                ConversionService delegate = this.delegates.get(i);
                if (delegate.canConvert(sourceType, targetType)) {
                    return delegate.convert(source, sourceType, targetType);
                }
            } catch (ConversionException e) {
                // ignored
            }
        }

        return this.delegates.get(this.delegates.size() - 1).convert(source, sourceType, targetType);
    }
}
