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
package org.apache.camel.impl;

import java.io.InputStream;
import java.net.URL;

import org.apache.camel.impl.engine.DefaultClassResolver;
import org.junit.Assert;
import org.junit.Test;

public class DefaultClassResolverTest extends Assert {

    @Test
    public void testResolveClass() {
        DefaultClassResolver resolver = new DefaultClassResolver();
        Class<?> clazz = resolver.resolveClass("java.lang.Integer");
        assertNotNull(clazz);
    }

    @Test
    public void testResolveClassType() {
        DefaultClassResolver resolver = new DefaultClassResolver();
        Class<Integer> clazz = resolver.resolveClass("java.lang.Integer", Integer.class);
        assertNotNull(clazz);
    }

    @Test
    public void testResolveClassClassLoader() {
        DefaultClassResolver resolver = new DefaultClassResolver();
        Class<?> clazz = resolver.resolveClass("java.lang.Integer", DefaultClassResolverTest.class.getClassLoader());
        assertNotNull(clazz);
    }

    @Test
    public void testResolveClassClassLoaderType() {
        DefaultClassResolver resolver = new DefaultClassResolver();
        Class<Integer> clazz = resolver.resolveClass("java.lang.Integer", Integer.class, DefaultClassResolverTest.class.getClassLoader());
        assertNotNull(clazz);
    }

    @Test
    public void testResolveMandatoryClass() throws Exception {
        DefaultClassResolver resolver = new DefaultClassResolver();
        Class<?> clazz = resolver.resolveMandatoryClass("java.lang.Integer");
        assertNotNull(clazz);
    }

    @Test
    public void testResolveMandatoryClassType() throws Exception {
        DefaultClassResolver resolver = new DefaultClassResolver();
        Class<Integer> clazz = resolver.resolveMandatoryClass("java.lang.Integer", Integer.class);
        assertNotNull(clazz);
    }

    @Test
    public void testResolveMandatorySimpleClassType() throws Exception {
        DefaultClassResolver resolver = new DefaultClassResolver();

        Class<Byte> clazz = resolver.resolveMandatoryClass("Byte", Byte.class);
        assertNotNull(clazz);
        clazz = resolver.resolveMandatoryClass("java.lang.Byte", Byte.class);
        assertNotNull(clazz);

        Class<Long> clazz2 = resolver.resolveMandatoryClass("Long", Long.class);
        assertNotNull(clazz2);
        clazz2 = resolver.resolveMandatoryClass("java.lang.Long", Long.class);
        assertNotNull(clazz2);

        Class<String> clazz3 = resolver.resolveMandatoryClass("String", String.class);
        assertNotNull(clazz3);
        clazz3 = resolver.resolveMandatoryClass("java.lang.String", String.class);
        assertNotNull(clazz3);

        Class<Byte[]> clazz4 = resolver.resolveMandatoryClass("Byte[]", Byte[].class);
        assertNotNull(clazz4);
        clazz4 = resolver.resolveMandatoryClass("java.lang.Byte[]", Byte[].class);
        assertNotNull(clazz4);

        Class<Object[]> clazz5 = resolver.resolveMandatoryClass("Object[]", Object[].class);
        assertNotNull(clazz5);
        clazz5 = resolver.resolveMandatoryClass("java.lang.Object[]", Object[].class);
        assertNotNull(clazz5);

        Class<String[]> clazz6 = resolver.resolveMandatoryClass("String[]", String[].class);
        assertNotNull(clazz6);
        clazz6 = resolver.resolveMandatoryClass("java.lang.String[]", String[].class);
        assertNotNull(clazz6);
    }

    @Test
    public void testResolveMandatoryClassClassLoader() throws Exception {
        DefaultClassResolver resolver = new DefaultClassResolver();
        Class<?> clazz = resolver.resolveMandatoryClass("java.lang.Integer", DefaultClassResolverTest.class.getClassLoader());
        assertNotNull(clazz);
    }

    @Test
    public void testResolveMandatoryClassClassLoaderType() throws Exception {
        DefaultClassResolver resolver = new DefaultClassResolver();
        Class<Integer> clazz = resolver.resolveMandatoryClass("java.lang.Integer", Integer.class, DefaultClassResolverTest.class.getClassLoader());
        assertNotNull(clazz);
    }

    @Test
    public void testResolveMandatoryClassNotFound() {
        DefaultClassResolver resolver = new DefaultClassResolver();
        try {
            resolver.resolveMandatoryClass("com.FooBar");
            fail("Should thrown an exception");
        } catch (ClassNotFoundException e) {
            // expected
        }
    }

    @Test
    public void testLoadResourceAsUri() {
        DefaultClassResolver resolver = new DefaultClassResolver();
        URL url = resolver.loadResourceAsURL("log4j2.properties");
        assertNotNull(url);
    }

    @Test
    public void testLoadResourceAsStream() {
        DefaultClassResolver resolver = new DefaultClassResolver();
        InputStream is = resolver.loadResourceAsStream("log4j2.properties");
        assertNotNull(is);
    }

}
