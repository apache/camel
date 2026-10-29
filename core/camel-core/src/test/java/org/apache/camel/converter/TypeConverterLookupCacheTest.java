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
package org.apache.camel.converter;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.impl.engine.DefaultInjector;
import org.apache.camel.support.TypeConverterSupport;
import org.apache.camel.support.scan.DefaultPackageScanClassResolver;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enum type converter asks the registry for a type converter on every conversion to an enum, and resolving one that
 * does not exist scans all the type converters, so the registry caches the results of lookup, also when no type
 * converter is found. A type converter that is added, removed or promoted must replace a cached result.
 */
public class TypeConverterLookupCacheTest {

    private final AtomicInteger resolved = new AtomicInteger();
    private final DefaultTypeConverter registry = new DefaultTypeConverter(
            new DefaultPackageScanClassResolver(), new DefaultInjector(new DefaultCamelContext()), true, false) {
        @Override
        protected TypeConverter doLookup(Class<?> toType, Class<?> fromType) {
            resolved.incrementAndGet();
            return super.doLookup(toType, fromType);
        }
    };

    @BeforeEach
    public void setUp() {
        ServiceHelper.startService(registry);
    }

    @AfterEach
    public void tearDown() {
        ServiceHelper.stopService(registry);
    }

    @Test
    public void testLookupIsResolvedOnce() {
        TypeConverter found = registry.lookup(Integer.class, String.class);
        assertNotNull(found);
        assertSame(found, registry.lookup(Integer.class, String.class));
        assertEquals(1, resolved.get());

        assertNull(registry.lookup(Foo.class, String.class));
        assertNull(registry.lookup(Foo.class, String.class));
        assertEquals(2, resolved.get());
    }

    @Test
    public void testAddedTypeConverterReplacesCachedMiss() {
        assertNull(registry.lookup(Foo.class, String.class));

        TypeConverter fooConverter = new FooConverter();
        registry.addTypeConverter(Foo.class, String.class, fooConverter);

        assertSame(fooConverter, registry.lookup(Foo.class, String.class));
    }

    @Test
    public void testRemovedTypeConverterReplacesCachedResult() {
        TypeConverter fooConverter = new FooConverter();
        registry.addTypeConverter(Foo.class, String.class, fooConverter);
        assertSame(fooConverter, registry.lookup(Foo.class, String.class));

        assertTrue(registry.removeTypeConverter(Foo.class, String.class));

        assertNull(registry.lookup(Foo.class, String.class));
    }

    @Test
    public void testFallbackThatConvertedReplacesCachedResult() {
        TypeConverter fallback = new FooConverter();
        registry.addFallbackTypeConverter(fallback, false);
        // lookup does not use the fallback type converters
        assertNotSame(fallback, registry.lookup(Foo.class, String.class));

        // a fallback that converted is used for this pair of types from now on, and lookup returns it too
        assertNotNull(registry.convertTo(Foo.class, "foo"));

        assertSame(fallback, registry.lookup(Foo.class, String.class));
    }

    @Test
    public void testEnumWithoutMatchingConstant() {
        assertEquals(MyTypeEnum.TYPE1, registry.convertTo(MyTypeEnum.class, "type1"));
        assertNull(registry.tryConvertTo(MyTypeEnum.class, "unknown"));

        TypeConversionException e
                = assertThrows(TypeConversionException.class, () -> registry.convertTo(MyTypeEnum.class, "unknown"));
        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("Enum class " + MyTypeEnum.class + " does not have any constant with value: unknown",
                cause.getMessage());
    }

    private static final class Foo {
    }

    private static final class FooConverter extends TypeConverterSupport {

        @Override
        public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
            return type == Foo.class ? type.cast(new Foo()) : null;
        }
    }
}
