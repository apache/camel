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

import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import org.apache.camel.core.xml.PatternBasedPackageScanFilter;
import org.apache.camel.util.CollectionHelper;

public class PatternBasedPackageScanFilterTest extends org.apache.camel.spring.scan.ScanTestSupport {

    private Set<Class<?>> allClasses;

    public void setUp() throws Exception {
        // use classes that are pretty much constants
        allClasses = new HashSet<Class<?>>();
        allClasses.add(List.class);
        allClasses.add(ArrayList.class);
        allClasses.add(LinkedList.class);
        allClasses.add(File.class);
        allClasses.add(FileInputStream.class);
        allClasses.add(RandomAccessFile.class);
        // contains file pattern but in util pkg not io
        allClasses.add(JarFile.class);
        super.setUp();
    }

    public void testNoPattersIncludeAllClasses() {
        validateMatchingSetContains(allClasses);
    }

    public void testIncludePatterns() {
        addIncludePatterns("*");
        validateMatchingSetContains(allClasses);

        filter = new PatternBasedPackageScanFilter();
        addIncludePatterns("java*");
        validateMatchingSetContains(allClasses);

        filter = new PatternBasedPackageScanFilter();
        addIncludePatterns("java.io.*");
        validateMatchingSetContains(File.class, FileInputStream.class, RandomAccessFile.class);

        filter = new PatternBasedPackageScanFilter();
        addIncludePatterns("java.util.**");
        validateMatchingSetContains(List.class, ArrayList.class, LinkedList.class, JarFile.class);

        filter = new PatternBasedPackageScanFilter();
        addIncludePatterns("java.io.*", "java.util.*");
        validateMatchingSetContains(allClasses);

        filter = new PatternBasedPackageScanFilter();
        addIncludePatterns("java.io.File");
        validateMatchingSetContains(File.class);

        filter = new PatternBasedPackageScanFilter();
        addIncludePatterns("java.io.File*");
        validateMatchingSetContains(File.class, FileInputStream.class);

        filter = new PatternBasedPackageScanFilter();
        addIncludePatterns("java.io.*File*");
        validateMatchingSetContains(File.class, FileInputStream.class, RandomAccessFile.class);

        filter = new PatternBasedPackageScanFilter();
        addIncludePatterns("java.**.*File*");
        validateMatchingSetContains(File.class, FileInputStream.class, RandomAccessFile.class, JarFile.class);

        filter = new PatternBasedPackageScanFilter();
        addIncludePatterns("java.util.*List");
        validateMatchingSetContains(List.class, ArrayList.class, LinkedList.class);

        filter = new PatternBasedPackageScanFilter();
        addIncludePatterns("java.lang", "java.lang.*");
        validateMatchingSetContains();

        filter = new PatternBasedPackageScanFilter();
        addIncludePatterns("java.lang", "java.lang.*");
        validateMatchingSetContains();
    }

    public void testExcludePatterns() {
        addExcludePatterns("*");
        validateMatchingSetContains();

        filter = new PatternBasedPackageScanFilter();
        addExcludePatterns("java*");
        validateMatchingSetContains();

        filter = new PatternBasedPackageScanFilter();
        addExcludePatterns("java.io.*");
        validateMatchingSetContains(List.class, ArrayList.class, LinkedList.class, JarFile.class);

        filter = new PatternBasedPackageScanFilter();
        addExcludePatterns("java.util.**");
        validateMatchingSetContains(File.class, FileInputStream.class, RandomAccessFile.class);

        filter = new PatternBasedPackageScanFilter();
        addExcludePatterns("java.io.*", "java.util.*");
        validateMatchingSetContains();

        filter = new PatternBasedPackageScanFilter();
        addExcludePatterns("java.io.File");
        validateMatchingSetContains(List.class, ArrayList.class, LinkedList.class, JarFile.class, FileInputStream.class, RandomAccessFile.class);

        filter = new PatternBasedPackageScanFilter();
        addExcludePatterns("java.io.File*");
        validateMatchingSetContains(List.class, ArrayList.class, LinkedList.class, JarFile.class, RandomAccessFile.class);

        filter = new PatternBasedPackageScanFilter();
        addExcludePatterns("java.io.*File*");
        validateMatchingSetContains(List.class, ArrayList.class, LinkedList.class, JarFile.class);

        filter = new PatternBasedPackageScanFilter();
        addExcludePatterns("java.**.*File*");
        validateMatchingSetContains(List.class, ArrayList.class, LinkedList.class);

        filter = new PatternBasedPackageScanFilter();
        addExcludePatterns("java.util.*List");
        validateMatchingSetContains(File.class, FileInputStream.class, RandomAccessFile.class, JarFile.class);

        filter = new PatternBasedPackageScanFilter();
        addExcludePatterns("java.lang", "java.lang.*");
        validateMatchingSetContains(allClasses);

        filter = new PatternBasedPackageScanFilter();
        addExcludePatterns("java.lang", "java.lang.*");
        validateMatchingSetContains(allClasses);
    }

    public void testExcludeHasPrecedenceOverInclude() {
        // include any classes from the util pkg but exclude those in jar
        addIncludePatterns("java.util.*");
        addExcludePatterns("java.util.jar.*");
        validateMatchingSetContains(List.class, ArrayList.class, LinkedList.class);
    }
    
    public void testBulkIncludeAdd() {
        // include any classes from the util pkg but exclude those in jar
        Set<String> includes = CollectionHelper.createSetContaining("java.io.*", "java.util.*");
        filter.addIncludePatterns(includes);
        validateMatchingSetContains(allClasses);
    }
    
    public void testBulkExcludeAdd() {
        // include any classes from the util pkg but exclude those in jar
        Set<String> excludes = CollectionHelper.createSetContaining("java.io.*", "java.util.*");
        filter.addExcludePatterns(excludes);
        validateMatchingSetContains();
    }

    protected void validateMatchingSetContains(Class<?>... matchingClasses) {
        super.validateMatchingSetContains(allClasses, matchingClasses);
    }

    protected void validateMatchingSetContains(Set<Class<?>> matchingClasses) {
        super.validateMatchingSetContains(allClasses, matchingClasses);
    }
}
