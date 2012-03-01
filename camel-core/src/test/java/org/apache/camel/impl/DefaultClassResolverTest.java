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
package org.apache.camel.impl;

import java.io.InputStream;
import java.net.URL;

import junit.framework.TestCase;

/**
 * @version 
 */
public class DefaultClassResolverTest extends TestCase {

    public void testResolveClass() {
        DefaultClassResolver resolver = new DefaultClassResolver();
        Class clazz = resolver.resolveClass("java.lang.Integer");
        assertNotNull(clazz);
    }

    public void testResolveClassType() {
        DefaultClassResolver resolver = new DefaultClassResolver();
        Class<Integer> clazz = resolver.resolveClass("java.lang.Integer", Integer.class);
        assertNotNull(clazz);
    }

    public void testResolveClassClassLoader() {
        DefaultClassResolver resolver = new DefaultClassResolver();
        Class clazz = resolver.resolveClass("java.lang.Integer", DefaultClassResolverTest.class.getClassLoader());
        assertNotNull(clazz);
    }

    public void testResolveClassClassLoaderType() {
        DefaultClassResolver resolver = new DefaultClassResolver();
        Class<Integer> clazz = resolver.resolveClass("java.lang.Integer", Integer.class, DefaultClassResolverTest.class.getClassLoader());
        assertNotNull(clazz);
    }

    public void testResolveMandatoryClass() throws Exception {
        DefaultClassResolver resolver = new DefaultClassResolver();
        Class clazz = resolver.resolveMandatoryClass("java.lang.Integer");
        assertNotNull(clazz);
    }

    public void testResolveMandatoryClassType()throws Exception {
        DefaultClassResolver resolver = new DefaultClassResolver();
        Class<Integer> clazz = resolver.resolveMandatoryClass("java.lang.Integer", Integer.class);
        assertNotNull(clazz);
    }

    public void testResolveMandatoryClassClassLoader() throws Exception {
        DefaultClassResolver resolver = new DefaultClassResolver();
        Class clazz = resolver.resolveMandatoryClass("java.lang.Integer", DefaultClassResolverTest.class.getClassLoader());
        assertNotNull(clazz);
    }

    public void testResolveMandatoryClassClassLoaderType() throws Exception {
        DefaultClassResolver resolver = new DefaultClassResolver();
        Class<Integer> clazz = resolver.resolveMandatoryClass("java.lang.Integer", Integer.class, DefaultClassResolverTest.class.getClassLoader());
        assertNotNull(clazz);
    }

    public void testResolveMandatoryClassNotFound()  {
        DefaultClassResolver resolver = new DefaultClassResolver();
        try {
            resolver.resolveMandatoryClass("com.FooBar");
            fail("Should thrown an exception");
        } catch (ClassNotFoundException e) {
            // expected
        }
    }

    public void testLoadResourceAsUri() {
        DefaultClassResolver resolver = new DefaultClassResolver();
        URL url = resolver.loadResourceAsURL("log4j.properties");
        assertNotNull(url);
    }

    public void testLoadResourceAsStream() {
        DefaultClassResolver resolver = new DefaultClassResolver();
        InputStream is = resolver.loadResourceAsStream("log4j.properties");
        assertNotNull(is);
    }

}
