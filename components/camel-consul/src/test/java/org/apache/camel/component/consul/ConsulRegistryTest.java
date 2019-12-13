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
package org.apache.camel.component.consul;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.orbitz.consul.Consul;
import org.apache.camel.NoSuchBeanException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for Camel Registry implementation for Consul
 */
public class ConsulRegistryTest implements Serializable {

    private static final long serialVersionUID = -3482971969351609265L;
    private static ConsulRegistry registry;
    private static GenericContainer container;

    public class ConsulTestClass implements Serializable {
        private static final long serialVersionUID = -4815945688487114891L;

        public String hello(String name) {
            return "Hello " + name;
        }
    }

    @BeforeAll
    public static void setUp() {
        container = ConsulTestSupport.consulContainer();
        container.start();

        registry = new ConsulRegistry(container.getContainerIpAddress(), container.getMappedPort(Consul.DEFAULT_HTTP_PORT));
    }

    @AfterAll
    public static void tearDown() {
        container.stop();
    }

    @Test
    public void storeString() {
        registry.put("stringTestKey", "stringValue");
        String result = (String)registry.lookupByName("stringTestKey");
        registry.remove("stringTestKey");
        assertNotNull(result);
        assertEquals("stringValue", result);
    }

    @Test
    public void overrideExistingKey() {
        registry.put("uniqueKey", "stringValueOne");
        registry.put("uniqueKey", "stringValueTwo");
        String result = (String)registry.lookupByName("uniqueKey");
        registry.remove("uniqueKey");
        assertNotNull(result);
        assertEquals("stringValueTwo", result);
    }

    @Test
    public void checkLookupByName() {
        registry.put("namedKey", "namedValue");
        String result = (String)registry.lookupByName("namedKey");
        registry.remove("namedKey");
        assertNotNull(result);
        assertEquals("namedValue", result);
    }

    @Test
    public void checkFailedLookupByName() {
        registry.put("namedKey", "namedValue");
        registry.remove("namedKey");
        String result = (String)registry.lookupByName("namedKey");
        assertNull(result);
    }

    @Test
    public void checkLookupByNameAndType() {
        ConsulTestClass consulTestClass = new ConsulTestClass();
        registry.put("testClass", consulTestClass);
        ConsulTestClass consulTestClassClone = registry.lookupByNameAndType("testClass", consulTestClass.getClass());
        registry.remove("testClass");
        assertNotNull(consulTestClassClone);
        assertEquals(consulTestClass.getClass(), consulTestClassClone.getClass());
    }

    @Test
    public void checkFailedLookupByNameAndType() {
        ConsulTestClass consulTestClass = new ConsulTestClass();
        registry.put("testClass", consulTestClass);
        registry.remove("testClass");
        ConsulTestClass consulTestClassClone = registry.lookupByNameAndType("testClass", consulTestClass.getClass());
        assertNull(consulTestClassClone);
    }

    @Test
    public void checkFindByTypeWithName() {
        ConsulTestClass consulTestClassOne = new ConsulTestClass();
        ConsulTestClass consulTestClassTwo = new ConsulTestClass();
        registry.put("testClassOne", consulTestClassOne);
        registry.put("testClassTwo", consulTestClassTwo);
        Map<String, ? extends ConsulTestClass> consulTestClassMap = registry.findByTypeWithName(consulTestClassOne.getClass());
        registry.remove("testClassOne");
        registry.remove("testClassTwo");
        HashMap<String, ConsulTestClass> emptyHashMap = new HashMap<>();
        assertNotNull(consulTestClassMap);
        assertEquals(consulTestClassMap.getClass(), emptyHashMap.getClass());
        assertEquals(2, consulTestClassMap.size());
    }

    public void checkFailedFindByTypeWithName() {

    }

    @Test
    public void storeObject() {
        ConsulTestClass testObject = new ConsulTestClass();
        registry.put("objectTestClass", testObject);
        ConsulTestClass clone = (ConsulTestClass)registry.lookupByName("objectTestClass");
        assertEquals(clone.hello("World"), "Hello World");
        registry.remove("objectTestClass");
    }

    @Test
    public void findByType() {
        ConsulTestClass classOne = new ConsulTestClass();
        registry.put("classOne", classOne);
        ConsulTestClass classTwo = new ConsulTestClass();
        registry.put("classTwo", classTwo);
        Set<? extends ConsulTestClass> results = registry.findByType(classOne.getClass());
        assertNotNull(results);
        HashSet<ConsulTestClass> hashSet = new HashSet<>();
        registry.remove("classOne");
        registry.remove("classTwo");
        assertEquals(results.getClass(), hashSet.getClass());
        assertEquals(2, results.size());
    }

    public void notFindByType() {

    }

    @Test
    public void deleteNonExisting() {
        assertThrows(NoSuchBeanException.class, () -> registry.remove("nonExisting"));
    }
}
