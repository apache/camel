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
package org.apache.camel.impl.scan;

import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.camel.impl.scan.test.ScannableOne;
import org.apache.camel.impl.scan.test.ScannableTwo;
import org.apache.camel.impl.scan.test.a.ScanTargetOne;
import org.apache.camel.impl.scan.test.b.ScanTargetTwo;
import org.apache.camel.impl.scan.test.c.ScanTargetThree;
import org.apache.camel.spi.PackageScanFilter;
import org.apache.camel.util.CollectionHelper;

public class PackageScanFiltersTest extends TestCase {

    public void testAssignableToPackageScanFilter() {
        AssignableToPackageScanFilter filter = new AssignableToPackageScanFilter();
        assertFalse(filter.matches(ScanTargetOne.class));

        filter = new AssignableToPackageScanFilter(ScanTargetOne.class);
        validateFilter(filter, ScanTargetOne.class);

        filter = new AssignableToPackageScanFilter(ScanTargetOne.class);
        validateFilter(filter, ScanTargetTwo.class);

        Set<Class> classes = new LinkedHashSet<Class>();
        classes.add(ScanTargetOne.class);
        classes.add(ScanTargetThree.class);
        filter = new AssignableToPackageScanFilter(classes);
        validateFilter(filter, ScanTargetOne.class);
        validateFilter(filter, ScanTargetTwo.class);
        validateFilter(filter, ScanTargetThree.class);

        assertTrue(filter.toString().contains("ScanTargetOne"));
        assertTrue(filter.toString().contains("ScanTargetThree"));
    }

    public void testAnnotatedWithAnyPackageScanFilter() {
        Set<Class<? extends Annotation>> annotations = new LinkedHashSet<Class<? extends Annotation>>();
        annotations.add(ScannableOne.class);
        annotations.add(ScannableTwo.class);

        AnnotatedWithAnyPackageScanFilter filter = new AnnotatedWithAnyPackageScanFilter(annotations);
        Class<ScanTargetOne> type = ScanTargetOne.class;
        validateFilter(filter, type);
        validateFilter(filter, ScanTargetThree.class);

        assertEquals("annotated with any @[[interface org.apache.camel.impl.scan.test.ScannableOne, interface org.apache.camel.impl.scan.test.ScannableTwo]]", filter.toString());
    }

    public void testAnnotatedWithPackageScanFilter() {
        AnnotatedWithPackageScanFilter filter = new AnnotatedWithPackageScanFilter(ScannableOne.class);
        validateFilter(filter, ScanTargetOne.class);
        validateFilter(filter, ScanTargetTwo.class);

        filter = new AnnotatedWithPackageScanFilter(ScannableTwo.class);
        validateFilter(filter, ScanTargetThree.class);
        assertEquals("annotated with @ScannableTwo", filter.toString());
    }

    public void testCompositePackageScanFilter() {
        PackageScanFilter one = new AnnotatedWithPackageScanFilter(ScannableOne.class);
        PackageScanFilter two = new AssignableToPackageScanFilter(ScanTargetOne.class);
        Set<PackageScanFilter> filters = CollectionHelper.createSetContaining(one, two);

        CompositePackageScanFilter filter = new CompositePackageScanFilter(filters);
        validateFilter(filter, ScanTargetOne.class);
        validateFilter(filter, ScanTargetTwo.class);

        filter = new CompositePackageScanFilter();
        filter.addFilter(one);
        filter.addFilter(two);
        validateFilter(filter, ScanTargetOne.class);
        validateFilter(filter, ScanTargetTwo.class);
    }

    public void testInvertingFilter() {
        InvertingPackageScanFilter filter = new InvertingPackageScanFilter(new AnnotatedWithPackageScanFilter(ScannableOne.class));
        validateFilter(filter, ScanTargetThree.class);
        assertEquals("![annotated with @ScannableOne]", filter.toString());
    }

    private void validateFilter(PackageScanFilter filter, Class type) {
        assertTrue(filter.matches(type));
        assertFalse(new InvertingPackageScanFilter(filter).matches(type));
    }

}
