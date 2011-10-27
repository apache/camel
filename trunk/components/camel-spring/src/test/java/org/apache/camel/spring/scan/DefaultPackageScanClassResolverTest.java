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
package org.apache.camel.spring.scan;

import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.impl.DefaultPackageScanClassResolver;
import org.apache.camel.spring.scan.a.ScanTargetOne;
import org.apache.camel.spring.scan.b.ScanTargetTwo;
import org.apache.camel.spring.scan.c.ScanTargetThree;

public class DefaultPackageScanClassResolverTest extends org.apache.camel.spring.scan.ScanTestSupport {

    private DefaultPackageScanClassResolver resolver;
    private Set<Class<? extends Annotation>> annotations = new HashSet<Class<? extends Annotation>>();
    private String scanPackage = "org.apache.camel.spring.scan";

    public void setUp() throws Exception {
        super.setUp();
        resolver = new DefaultPackageScanClassResolver();
        annotations.add(org.apache.camel.spring.scan.ScannableOne.class);
        annotations.add(org.apache.camel.spring.scan.ScannableTwo.class);
    }
    
    public void testAccepableSchema() {
        assertFalse("We should not accept the test by default!", resolver.isAcceptableScheme("test://test"));
        resolver.setAcceptableSchemes("test:;test2:");
        assertTrue("We should accept the test:!", resolver.isAcceptableScheme("test://test"));
        assertTrue("We should accept the test2:!", resolver.isAcceptableScheme("test2://test"));
    }

    public void testFindByAnnotationWithoutExtraFilters() {
        Set<Class<?>> scanned = resolver.findAnnotated(org.apache.camel.spring.scan.ScannableOne.class, scanPackage);
        validateMatchingSetContains(scanned, ScanTargetOne.class, ScanTargetTwo.class);

        scanned = resolver.findAnnotated(org.apache.camel.spring.scan.ScannableTwo.class, scanPackage);
        validateMatchingSetContains(scanned, ScanTargetThree.class);
    }

    public void testFindByAnnotationsWithoutExtraFilters() {
        Set<Class<?>> scanned = resolver.findAnnotated(annotations, scanPackage);
        validateMatchingSetContains(scanned, ScanTargetOne.class, ScanTargetTwo.class, ScanTargetThree.class);
    }

    public void testFindImplementationsWithoutExtraFilters() {
        Set<Class<?>> scanned = resolver.findImplementations(ScanTargetOne.class, scanPackage);
        validateMatchingSetContains(scanned, ScanTargetOne.class, ScanTargetTwo.class);
    }
    
    public void testFindByAnnotationWithIncludePackageFilter() {
        filter.addIncludePattern(scanPackage + ".b.*");
        resolver.addFilter(filter);

        Set<Class<?>> scanned = resolver.findAnnotated(org.apache.camel.spring.scan.ScannableOne.class, scanPackage);
        validateMatchingSetContains(scanned, ScanTargetTwo.class);

        scanned = resolver.findAnnotated(ScannableTwo.class, scanPackage);
        validateMatchingSetContains(scanned);
    }

    public void testFindByAnnotationsWithIncludePackageFilter() {
        filter.addIncludePattern(scanPackage + ".b.*");
        filter.addIncludePattern(scanPackage + ".c.*");
        resolver.addFilter(filter);

        Set<Class<?>> scanned = resolver.findAnnotated(annotations, "org.apache.camel.spring.scan");
        validateMatchingSetContains(scanned, ScanTargetTwo.class, ScanTargetThree.class);
    }

    public void testFindByAnnotationWithExcludePackageFilter() {
        filter.addExcludePattern(scanPackage + ".b.*");
        filter.addExcludePattern(scanPackage + ".c.*");
        resolver.addFilter(filter);

        Set<Class<?>> scanned = resolver.findAnnotated(ScannableOne.class, scanPackage);
        validateMatchingSetContains(scanned, ScanTargetOne.class);

        scanned = resolver.findAnnotated(org.apache.camel.spring.scan.ScannableTwo.class, scanPackage);
        validateMatchingSetContains(scanned);
    }

    public void testFindByAnnotationsWithExcludePackageFilter() {
        filter.addExcludePattern(scanPackage + ".a.*");

        Set<Class<?>> scanned = resolver.findAnnotated(annotations, "org.apache.camel.spring.scan");
        validateMatchingSetContains(scanned, ScanTargetTwo.class, ScanTargetThree.class);
    }

    public void testFindByFilterWithIncludePackageFilter() {
        filter.addIncludePattern(scanPackage + ".**.ScanTarget*");
        resolver.addFilter(filter);

        Set<Class<?>> scanned = resolver.findByFilter(filter, "org.apache.camel.spring.scan");
        validateMatchingSetContains(scanned, ScanTargetOne.class, ScanTargetTwo.class, ScanTargetThree.class);
    }
    
    public void testFindImplementationsWithIncludePackageFilter() {
        filter.addIncludePattern(scanPackage + ".b.*");
        resolver.addFilter(filter);
        
        Set<Class<?>> scanned = resolver.findImplementations(ScanTargetOne.class, scanPackage);
        validateMatchingSetContains(scanned,  ScanTargetTwo.class);
    }
    
    public void testFindImplementationsWithExcludePackageFilter() {
        filter.addExcludePattern(scanPackage + ".a.*");
        resolver.addFilter(filter);
        
        Set<Class<?>> scanned = resolver.findImplementations(ScanTargetOne.class, scanPackage);
        validateMatchingSetContains(scanned,  ScanTargetTwo.class);
    }
    
    public void testFindByFilterPackageInJarUrl() throws Exception {
        ClassLoader savedClassLoader = null;
        try {
            savedClassLoader = Thread.currentThread().getContextClassLoader();
            URL url = getClass().getResource("/package_scan_test.jar");
            
            URL urls[] = {new URL("jar:" + url.toString() + "!/")};
            URLClassLoader classLoader = new URLClassLoader(urls, savedClassLoader);

            Thread.currentThread().setContextClassLoader(classLoader);

            // recreate resolver since we mess with context class loader
            resolver = new DefaultPackageScanClassResolver();

            filter.addIncludePattern("a.*.c.*");
            resolver.addFilter(filter);
            Set<Class<?>> scanned = resolver.findByFilter(filter, "a.b.c");
            assertEquals(1, scanned.size());
            assertEquals("class a.b.c.Test", scanned.iterator().next().toString());            
        } finally {
            if (savedClassLoader != null) {
                Thread.currentThread().setContextClassLoader(savedClassLoader);
            } 
        }
    }
    
    public void testFindByFilterPackageInJarUrlWithPlusChars() throws Exception {
        ClassLoader savedClassLoader = null;
        try {
            savedClassLoader = Thread.currentThread().getContextClassLoader();
            URL url = getClass().getResource("/package+scan+test.jar");

            URL urls[] = {new URL("jar:" + url.toString() + "!/")};
            URLClassLoader classLoader = new URLClassLoader(urls, savedClassLoader);

            Thread.currentThread().setContextClassLoader(classLoader);

            // recreate resolver since we mess with context class loader
            resolver = new DefaultPackageScanClassResolver();

            filter.addIncludePattern("a.*.c.*");
            resolver.addFilter(filter);
            Set<Class<?>> scanned = resolver.findByFilter(filter, "a.b.c");
            assertEquals(1, scanned.size());
            assertEquals("class a.b.c.Test", scanned.iterator().next().toString());
        } finally {
            if (savedClassLoader != null) {
                Thread.currentThread().setContextClassLoader(savedClassLoader);
            }
        }
    }
}
