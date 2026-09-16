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
package org.apache.camel.support;

import java.util.List;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.spi.IdempotentRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PojoBeanHelperTest {

    private final CamelContext context = new DefaultCamelContext();

    @Test
    public void testFindAllReadsEveryJar() {
        List<PojoBeanHelper.PojoBean> beans = PojoBeanHelper.findAll(context);

        // camel-core-processor and camel-support both ship beans
        assertTrue(beans.stream().anyMatch(b -> b.name().equals("UseLatestAggregationStrategy")), beans.toString());
        assertTrue(beans.stream().anyMatch(b -> b.name().equals("MemoryIdempotentRepository")), beans.toString());
        assertTrue(beans.stream().anyMatch(b -> b.name().equals("MemoryAggregationRepository")), beans.toString());
        // no duplicates when the same jar is visible through more than one class loader
        assertEquals(beans.size(), beans.stream().map(PojoBeanHelper.PojoBean::javaType).distinct().count());
    }

    @Test
    public void testFindByName() {
        PojoBeanHelper.PojoBean bean = PojoBeanHelper.findByName(context, "com.foo.UseLatestAggregationStrategy");
        assertNotNull(bean);
        assertEquals("UseLatestAggregationStrategy", bean.name());
        assertEquals("org.apache.camel.processor.aggregate.UseLatestAggregationStrategy", bean.javaType());
        assertEquals("org.apache.camel.AggregationStrategy", bean.interfaceType());
        assertEquals("org.apache.camel", bean.groupId());
        assertEquals("camel-core-processor", bean.artifactId());

        // simple name, case-insensitive
        assertNotNull(PojoBeanHelper.findByName(context, "memoryAggregationRepository"));
        assertNull(PojoBeanHelper.findByName(context, "com.foo.MyBean"));
        assertNull(PojoBeanHelper.findByName(context, null));
    }

    @Test
    public void testBeansOfInterface() {
        List<PojoBeanHelper.PojoBean> beans = PojoBeanHelper.beansOfInterface(context, IdempotentRepository.class.getName());
        assertTrue(beans.stream().anyMatch(b -> b.name().equals("MemoryIdempotentRepository")), beans.toString());
        assertTrue(beans.stream().anyMatch(b -> b.name().equals("FileIdempotentRepository")), beans.toString());
        assertTrue(beans.stream().allMatch(b -> b.interfaceType().equals(IdempotentRepository.class.getName())));

        // by simple name too
        assertEquals(beans.size(), PojoBeanHelper.beansOfInterface(context, "IdempotentRepository").size());
        assertTrue(PojoBeanHelper.beansOfInterface(context, "com.foo.Unknown").isEmpty());
    }

    @Test
    public void testClassNotFoundHintWrongPackage() {
        String hint = PojoBeanHelper.classNotFoundHint(context, "com.foo.UseLatestAggregationStrategy",
                AggregationStrategy.class);
        assertEquals(" (did you mean org.apache.camel.processor.aggregate.UseLatestAggregationStrategy"
                     + " (org.apache.camel.AggregationStrategy)?)",
                hint);
        // the expected type does not matter when the name is known
        assertEquals(hint, PojoBeanHelper.classNotFoundHint(context, "com.foo.UseLatestAggregationStrategy", null));
        assertEquals(hint, PojoBeanHelper.classNotFoundHint(context, "UseLatestAggregationStrategy", Object.class));
        // a #class: reference, with or without constructor parameters or a factory method
        assertEquals(hint, PojoBeanHelper.classNotFoundHint(context, "#class:com.foo.UseLatestAggregationStrategy", null));
        assertEquals(hint,
                PojoBeanHelper.classNotFoundHint(context, "#class:com.foo.UseLatestAggregationStrategy('a', 1)", null));
        assertEquals(hint,
                PojoBeanHelper.classNotFoundHint(context, "#class:com.foo.UseLatestAggregationStrategy#create", null));
        // other references are bean names, not classes
        assertEquals("", PojoBeanHelper.classNotFoundHint(context, "#bean:UseLatestAggregationStrategy", null));
        assertEquals("", PojoBeanHelper.classNotFoundHint(context, "#UseLatestAggregationStrategy", null));
    }

    @Test
    public void testClassNotFoundHintUnknownClass() {
        assertEquals(" (check the package name; a class from another library needs its dependency added)",
                PojoBeanHelper.classNotFoundHint(context, "com.foo.MyBean", null));
        assertEquals(" (check the package name; a class from another library needs its dependency added)",
                PojoBeanHelper.classNotFoundHint(context, "com.foo.MyBean", Object.class));
        assertEquals(" (check the package name; a class from another library needs its dependency added)",
                PojoBeanHelper.classNotFoundHint(context, "com.foo.MyBean", Runnable.class));
        assertEquals("", PojoBeanHelper.classNotFoundHint(context, null, AggregationStrategy.class));
    }

    @Test
    public void testClassNotFoundHintListsBuiltInBeansOfTheExpectedType() {
        String hint = PojoBeanHelper.classNotFoundHint(context, "com.foo.MyRepo", AggregationRepository.class);
        assertTrue(hint.startsWith(" (check the package name; a class from another library needs its dependency added;"
                                   + " the built-in AggregationRepository beans are "),
                hint);
        assertTrue(hint.contains(
                "MemoryAggregationRepository (org.apache.camel.processor.aggregate.MemoryAggregationRepository)"), hint);
        assertTrue(hint.endsWith(")"), hint);
        assertFalse(hint.contains("IdempotentRepository"), hint);
    }

    @Test
    public void testMissingClassName() {
        assertEquals("com.foo.Bar", PojoBeanHelper.missingClassName(new ClassNotFoundException("com.foo.Bar")));
        assertEquals("com.foo.Bar", PojoBeanHelper.missingClassName(
                new RuntimeException("wrapped", new ClassNotFoundException("com.foo.Bar"))));
        assertEquals("com.foo.Bar", PojoBeanHelper.missingClassName(new NoClassDefFoundError("com/foo/Bar")));
        assertNull(PojoBeanHelper.missingClassName(new IllegalStateException("nope")));
        assertNull(PojoBeanHelper.missingClassName(null));
    }
}
