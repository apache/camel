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
package org.apache.camel.impl.engine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;

import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.Injector;
import org.junit.Test;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DefaultFactoryFinderTest {

    public static class TestImplA implements TestType {
    }

    public static class TestImplB implements TestType {
    }

    public interface TestType {
    }

    private static final String TEST_RESOURCE_PATH = "/org/apache/camel/impl/";

    final DefaultFactoryFinder factoryFinder = new DefaultFactoryFinder(new DefaultClassResolver(), TEST_RESOURCE_PATH);

    @Test
    public void shouldComplainIfClassResolverCannotResolveClass() throws IOException {
        final ClassResolver classResolver = mock(ClassResolver.class);

        final String properties = "class=" + TestImplA.class.getName();

        when(classResolver.loadResourceAsStream("/org/apache/camel/impl/TestImplA")).thenReturn(new ByteArrayInputStream(properties.getBytes()));

        when(classResolver.resolveClass(TestImplA.class.getName())).thenReturn(null);

        final DefaultFactoryFinder factoryFinder = new DefaultFactoryFinder(classResolver, TEST_RESOURCE_PATH);

        assertFalse(factoryFinder.findClass("TestImplA", null).isPresent());
    }

    @Test
    public void shouldComplainIfInstanceTypeIsNotAsExpected() throws ClassNotFoundException, IOException {
        final Injector injector = mock(Injector.class);

        final TestImplA expected = new TestImplA();
        when(injector.newInstance(TestImplA.class, false)).thenReturn(expected);

        try {
            factoryFinder.newInstance("TestImplA", TestImplB.class);
            fail("Exception should have been thrown");
        } catch (Exception e) {
            assertTrue(e instanceof ClassCastException);
        }
    }

    @Test
    public void shouldComplainIfUnableToCreateNewInstances() throws ClassNotFoundException, IOException {
        assertFalse(factoryFinder.newInstance("TestImplX").isPresent());
    }

    @Test
    public void shouldComplainNoClassKeyInPropertyFile() throws ClassNotFoundException {
        try {
            factoryFinder.findClass("TestImplNoProperty");
            fail("NoFactoryAvailableException should have been thrown");
        } catch (Exception e) {
            assertEquals("Expected property is missing: class", e.getCause().getMessage());
        }
    }

    @Test
    public void shouldCreateNewInstances() throws ClassNotFoundException, IOException {
        final Object instance = factoryFinder.newInstance("TestImplA").get();

        assertThat(instance, instanceOf(TestImplA.class));
    }

    @Test
    public void shouldFindSingleClass() throws ClassNotFoundException, IOException {
        final Class<?> clazz = factoryFinder.findClass("TestImplA").orElse(null);

        assertEquals(TestImplA.class, clazz);
    }

    @Test
    public void shouldFindSingleClassFromClassMap() throws ClassNotFoundException, IOException {
        final DefaultFactoryFinder factoryFinder = new DefaultFactoryFinder(null, null);
        factoryFinder.addToClassMap("prefixkey", () -> TestImplA.class);

        final Class<?> clazz = factoryFinder.findClass("key", "prefix").orElse(null);

        assertEquals(TestImplA.class, clazz);
    }

    @Test
    public void shouldFindSingleClassWithPropertyPrefix() throws ClassNotFoundException, IOException {
        final Class<?> clazz = factoryFinder.findClass("TestImplA", "prefix.").orElse(null);

        assertEquals(TestImplA.class, clazz);
    }

    @Test
    public void shouldFindSingleClassWithPropertyPrefixAndExpectedType() throws ClassNotFoundException, IOException {
        final Class<?> clazz = factoryFinder.findClass("TestImplA", "prefix.", TestType.class).orElse(null);

        assertEquals(TestImplA.class, clazz);
    }

    URL urlFor(final Class<?> clazz) {
        final String resourceName = clazz.getPackage().getName().replace('.', '/') + "/" + clazz.getSimpleName() + ".properties";
        final ClassLoader classLoader = clazz.getClassLoader();

        return classLoader.getResource(resourceName);
    }
}
