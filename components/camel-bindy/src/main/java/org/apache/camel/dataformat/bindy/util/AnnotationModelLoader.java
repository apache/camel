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
package org.apache.camel.dataformat.bindy.util;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.FixedLengthRecord;
import org.apache.camel.dataformat.bindy.annotation.Link;
import org.apache.camel.dataformat.bindy.annotation.Message;
import org.apache.camel.dataformat.bindy.annotation.Section;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PackageScanFilter;

/**
 * Annotation based loader for model classes with Bindy annotations.
 */
public class AnnotationModelLoader {

    private PackageScanClassResolver resolver;
    private PackageScanFilter filter;
    private Set<Class<? extends Annotation>> annotations;

    public AnnotationModelLoader(PackageScanClassResolver resolver) {
        this.resolver = resolver;

        annotations = new LinkedHashSet<Class<? extends Annotation>>();
        annotations.add(CsvRecord.class);
        annotations.add(Link.class);
        annotations.add(Message.class);
        annotations.add(Section.class);
        annotations.add(FixedLengthRecord.class);
    }
    
    public AnnotationModelLoader(PackageScanClassResolver resolver, PackageScanFilter filter) {
        this(resolver);
        this.filter = filter;
    }

    public Set<Class<?>> loadModels(String... packageNames) throws Exception {
        Set<Class<?>> results = resolver.findAnnotated(annotations, packageNames);
        
        //TODO;  this logic could be moved into the PackageScanClassResolver by creating:
        //          findAnnotated(annotations, packageNames, filter) 
        Set<Class<?>> resultsToRemove = new HashSet<Class<?>>();
        if (filter != null) {
            for (Class<?> clazz : results) {
                if (!filter.matches(clazz)) {
                    resultsToRemove.add(clazz);
                }
            }
        }
        results.removeAll(resultsToRemove);
        return results;
    }
    
}
