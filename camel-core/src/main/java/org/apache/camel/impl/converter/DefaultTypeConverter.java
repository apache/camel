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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.TypeConverterAware;
import org.apache.camel.util.FactoryFinder;
import org.apache.camel.util.NoFactoryAvailableException;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Default implementation of a type converter registry used for
 * <a href="http://activemq.apache.org/camel/type-converter.html">type converters</a> in Camel.
 *
 * @version $Revision$
 */
public class DefaultTypeConverter implements TypeConverter, TypeConverterRegistry {
    private static final transient Log LOG = LogFactory.getLog(DefaultTypeConverter.class);
    private final Map<TypeMapping, TypeConverter> typeMappings = new HashMap<TypeMapping, TypeConverter>();
    private Injector injector;
    private List<TypeConverterLoader> typeConverterLoaders = new ArrayList<TypeConverterLoader>();
    private List<TypeConverter> fallbackConverters = new ArrayList<TypeConverter>();
    private boolean loaded;

    public DefaultTypeConverter(Injector injector) {
        typeConverterLoaders.add(new AnnotationTypeConverterLoader());
        this.injector = injector;
        addFallbackConverter(new AsyncProcessorTypeConverter());
        addFallbackConverter(new PropertyEditorTypeConverter());
        addFallbackConverter(new ToStringTypeConverter());
        addFallbackConverter(new ArrayTypeConverter());
        addFallbackConverter(new EnumTypeConverter());
    }

    public <T> T convertTo(Class<T> toType, Object value) {
        if (toType.isInstance(value)) {
            return toType.cast(value);
        }
        checkLoaded();
        TypeConverter converter = getOrFindTypeConverter(toType, value);
        if (converter != null) {
            return converter.convertTo(toType, value);
        }

        for (TypeConverter fallback : fallbackConverters) {
            T rc = fallback.convertTo(toType, value);
            if (rc != null) {
                return rc;
            }
        }

        // lets avoid NullPointerException when converting to boolean for null values
        if (boolean.class.isAssignableFrom(toType)) {
            return (T) Boolean.FALSE;
        }
        if (toType.isPrimitive()) {
            Class primitiveType = ObjectHelper.convertPrimitiveTypeToWrapperType(toType);
            if (primitiveType != toType) {
                return (T) convertTo(primitiveType, value);
            }
        }
        return null;
    }

    public void addTypeConverter(Class toType, Class fromType, TypeConverter typeConverter) {
        TypeMapping key = new TypeMapping(toType, fromType);
        synchronized (typeMappings) {
            TypeConverter converter = typeMappings.get(key);
            if (converter != null) {
                LOG.warn("Overriding type converter from: " + converter + " to: " + typeConverter);
            }
            typeMappings.put(key, typeConverter);
        }
    }

    public void addFallbackConverter(TypeConverter converter) {
        fallbackConverters.add(converter);
        if (converter instanceof TypeConverterAware) {
            TypeConverterAware typeConverterAware = (TypeConverterAware)converter;
            typeConverterAware.setTypeConverter(this);
        }
    }

    public TypeConverter getTypeConverter(Class toType, Class fromType) {
        TypeMapping key = new TypeMapping(toType, fromType);
        synchronized (typeMappings) {
            return typeMappings.get(key);
        }
    }

    public Injector getInjector() {
        return injector;
    }

    public void setInjector(Injector injector) {
        this.injector = injector;
    }

    protected <T> TypeConverter getOrFindTypeConverter(Class toType, Object value) {
        Class fromType = null;
        if (value != null) {
            fromType = value.getClass();
        }
        TypeMapping key = new TypeMapping(toType, fromType);
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
        // lets try the super classes of the from type
        if (fromType != null) {
            Class fromSuperClass = fromType.getSuperclass();
            if (fromSuperClass != null && !fromSuperClass.equals(Object.class)) {

                TypeConverter converter = getTypeConverter(toType, fromSuperClass);
                if (converter == null) {
                    converter = findTypeConverter(toType, fromSuperClass, value);
                }
                if (converter != null) {
                    return converter;
                }
            }
            for (Class type : fromType.getInterfaces()) {
                TypeConverter converter = getTypeConverter(toType, type);
                if (converter != null) {
                    return converter;
                }
            }

            // lets test for arrays
            if (fromType.isArray() && !fromType.getComponentType().isPrimitive()) {
                // TODO can we try walking the inheritance-tree for the element types?
                if (!fromType.equals(Object[].class)) {
                    fromSuperClass = Object[].class;

                    TypeConverter converter = getTypeConverter(toType, fromSuperClass);
                    if (converter == null) {
                        converter = findTypeConverter(toType, fromSuperClass, value);
                    }
                    if (converter != null) {
                        return converter;
                    }
                }
            }

            // lets test for Object based converters
            if (!fromType.equals(Object.class)) {
                TypeConverter converter = getTypeConverter(toType, Object.class);
                if (converter != null) {
                    return converter;
                }
            }
        }

        // lets try classes derived from this toType
        if (fromType != null) {
            Set<Map.Entry<TypeMapping, TypeConverter>> entries = typeMappings.entrySet();
            for (Map.Entry<TypeMapping, TypeConverter> entry : entries) {
                TypeMapping key = entry.getKey();
                Class aToType = key.getToType();
                if (toType.isAssignableFrom(aToType)) {
                    if (key.getFromType().isAssignableFrom(fromType)) {
                        return entry.getValue();
                    }
                }
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
            try {
                for (TypeConverterLoader typeConverterLoader : typeConverterLoaders) {
                    typeConverterLoader.load(this);
                }

                // lets try load any other fallback converters
                try {
                    loadFallbackTypeConverters();
                } catch (NoFactoryAvailableException e) {
                    // ignore its fine to have none
                }
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        }
    }

    protected void loadFallbackTypeConverters() throws IOException, ClassNotFoundException {
        FactoryFinder finder = new FactoryFinder();
        List<TypeConverter> converters = finder.newInstances("FallbackTypeConverter", getInjector(),
                                                             TypeConverter.class);
        for (TypeConverter converter : converters) {
            addFallbackConverter(converter);
        }
    }

    /**
     * Represents a mapping from one type (which can be null) to another
     */
    protected static class TypeMapping {
        Class toType;
        Class fromType;

        public TypeMapping(Class toType, Class fromType) {
            this.toType = toType;
            this.fromType = fromType;
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
                TypeMapping that = (TypeMapping)object;
                return ObjectHelper.equal(this.fromType, that.fromType)
                       && ObjectHelper.equal(this.toType, that.toType);
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

        @Override
        public String toString() {
            return "[" + fromType + "=>" + toType + "]";
        }
    }
}
