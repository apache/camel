/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.impl.converter;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @version $Revision$
 */
public class DefaultTypeConverter implements TypeConverter, TypeConverterRegistry {
    private static final transient Log log = LogFactory.getLog(DefaultTypeConverter.class);
    private Map<TypeMapping, TypeConverter> typeMappings = new HashMap<TypeMapping, TypeConverter>();
    private Injector injector;
    private List<TypeConverterLoader> typeConverterLoaders = new ArrayList<TypeConverterLoader>();
    private boolean loaded;

    public DefaultTypeConverter() {
        typeConverterLoaders.add(new AnnotationTypeConverterLoader());
    }

    public <T> T convertTo(Class<T> toType, Object value) {
        if (toType.isInstance(value)) {
            return toType.cast(value);
        }
        checkLoaded();
        TypeConverter converter = getConverter(toType, value);
        if (converter != null) {
            return converter.convertTo(toType, value);
        }
        if (value != null) {
            if (toType.equals(String.class)) {
                return (T) value.toString();
            }
        }
        return null;
    }

    public void addTypeConverter(Class fromType, Class toType, TypeConverter typeConverter) {
        TypeMapping key = new TypeMapping(fromType, toType);
        synchronized (typeMappings) {
            TypeConverter converter = typeMappings.get(key);
            if (converter != null) {
                log.warn("Overriding type converter from: " + converter + " to: " + typeConverter);
            }
            typeMappings.put(key, typeConverter);
        }
    }

    public TypeConverter getTypeConverter(Class fromType, Class toType) {
        TypeMapping key = new TypeMapping(fromType, toType);
        synchronized (typeMappings) {
            return typeMappings.get(key);
        }
    }

    public Injector getInjector() {
        if (injector == null) {
            injector = new ReflectionInjector();
        }
        return injector;
    }

    public void setInjector(Injector injector) {
        this.injector = injector;
    }

    protected <T> TypeConverter getConverter(Class toType, Object value) {
        Class fromType = null;
        if (value != null) {
            fromType = value.getClass();
        }
        TypeMapping key = new TypeMapping(fromType, toType);
        TypeConverter converter;
        synchronized (typeMappings) {
            converter = typeMappings.get(key);
            if (converter == null) {
                converter = findTypeConverter(toType, fromType, value);
                if (converter != null) {
                    typeMappings.put(key, converter);
                }
            }
        }
        return converter;
    }

    /**
     * Tries to auto-discover any available type converters
     */
    protected TypeConverter findTypeConverter(Class toType, Class fromType, Object value) {
        // lets try the super classes of the to type
        for (Class toSuperClass = toType.getSuperclass();
             toSuperClass != null && !toSuperClass.equals(Object.class);
             toSuperClass = toSuperClass.getSuperclass()) {

            TypeConverter converter = getTypeConverter(fromType, toSuperClass);
            if (converter != null) {
                return converter;
            }
        }

        // TODO should we filter out any interfaces which are super-interfaces?
        for (Class type : toType.getInterfaces()) {
            TypeConverter converter = getTypeConverter(fromType, type);
            if (converter != null) {
                return converter;
            }
        }

        // lets try the super classes of the from type
        Class fromSuperClass = fromType.getSuperclass();
        if (fromSuperClass != null && !fromSuperClass.equals(Object.class)) {
            return findTypeConverter(toType, fromSuperClass, value);
        }
        for (Class type : fromType.getInterfaces()) {
            TypeConverter converter = getTypeConverter(type, toType);
            if (converter != null) {
                return converter;
            }
        }
        // TODO look at constructors of toType?
        return null;
    }

    /**
     * Checks if the registry is loaded and if not lazily load it
     */
    protected synchronized void checkLoaded() {
        if (!loaded) {
            loaded = true;
            for (TypeConverterLoader typeConverterLoader : typeConverterLoaders) {
                try {
                    typeConverterLoader.load(this);
                }
                catch (Exception e) {
                    throw new RuntimeCamelException(e);
                }
            }
        }
    }

    /**
     * Represents a mapping from one type (which can be null) to another
     */
    protected static class TypeMapping {
        Class fromType;
        Class toType;

        public TypeMapping(Class fromType, Class toType) {
            this.fromType = fromType;
            this.toType = toType;
        }

        public Class getFromType() {
            return fromType;
        }

        public Class getToType() {
            return toType;
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof TypeMapping) {
                TypeMapping that = (TypeMapping) object;
                return ObjectHelper.equals(this.fromType, that.fromType) && ObjectHelper.equals(this.toType, that.toType);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int answer = toType.hashCode();
            if (fromType != null) {
                answer *= 37 + fromType.hashCode();
            }
            return answer;
        }
    }
}
