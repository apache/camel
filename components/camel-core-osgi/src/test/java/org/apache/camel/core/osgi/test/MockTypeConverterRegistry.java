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
package org.apache.camel.core.osgi.test;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.LoggingLevel;
import org.apache.camel.TypeConverter;
import org.apache.camel.TypeConverterExists;
import org.apache.camel.TypeConverters;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.TypeConverterRegistry;

public class MockTypeConverterRegistry implements TypeConverterRegistry {
    private List<TypeConverter> typeConverters = new ArrayList<TypeConverter>();
    private List<TypeConverter> fallbackTypeConverters = new ArrayList<TypeConverter>();
    
    public List<TypeConverter> getTypeConverters() {
        return typeConverters;
    }
    
    public List<TypeConverter> getFallbackTypeConverters() {
        return fallbackTypeConverters;
    }
    
    public void addTypeConverter(Class<?> toType, Class<?> fromType, TypeConverter typeConverter) {
        typeConverters.add(typeConverter);
    }

    public void addTypeConverters(TypeConverters typeConverters) {
        // noop
    }

    public boolean removeTypeConverter(Class<?> toType, Class<?> fromType) {
        // noop
        return true;
    }

    public void addFallbackTypeConverter(TypeConverter typeConverter, boolean canPromote) {
        fallbackTypeConverters.add(typeConverter);
    }

    public TypeConverter lookup(Class<?> toType, Class<?> fromType) {       
        return null;
    }

    public List<Class<?>[]> listAllTypeConvertersFromTo() {
        return null;
    }

    public void setInjector(Injector injector) {
       // do nothing
    }

    public Injector getInjector() {
        return null;
    }

    public Statistics getStatistics() {
        return null;
    }

    public int size() {
        return typeConverters.size();
    }

    public LoggingLevel getTypeConverterExistsLoggingLevel() {
        return LoggingLevel.WARN;
    }

    public void setTypeConverterExistsLoggingLevel(LoggingLevel loggingLevel) {
        // noop
    }

    public TypeConverterExists getTypeConverterExists() {
        return TypeConverterExists.Override;
    }

    public void setTypeConverterExists(TypeConverterExists typeConverterExists) {
        // noop
    }

    public void start() throws Exception {
        // noop
    }

    public void stop() throws Exception {
        // noop
    }
}

