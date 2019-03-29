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
package org.apache.camel.spring.spi;

import java.util.Collections;
import java.util.List;

import org.apache.camel.spring.SpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ParentContextRegistryTest extends SpringTestSupport {
    private static final List<String> EXPECTED_BEAN = Collections.singletonList("TestValue");

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        ClassPathXmlApplicationContext parentContext = new ClassPathXmlApplicationContext(
                "parentContextRegistryTestParent.xml", ParentContextRegistryTest.class);
        return new ClassPathXmlApplicationContext(
                new String[]{"parentContextRegistryTestChild.xml"},
                ParentContextRegistryTest.class, parentContext
        );
    }

    @Test
    public void testLookupByName() {
        assertEquals(EXPECTED_BEAN, context.getRegistry().lookupByName("testParentBean"));
    }

    @Test
    public void testLookupByNameAndType() {
        assertEquals(EXPECTED_BEAN, context.getRegistry().lookupByNameAndType("testParentBean", List.class));
    }

    @Test
    public void testFindByType() {
        assertEquals(Collections.singleton(EXPECTED_BEAN), context.getRegistry().findByType(List.class));
    }

    @Test
    public void testFindByTypeWithName() {
        assertEquals(Collections.singletonMap("testParentBean", EXPECTED_BEAN),
                context.getRegistry().findByTypeWithName(List.class));
    }
}
