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
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.Link;
import org.apache.camel.dataformat.bindy.annotation.Message;
import org.apache.camel.spi.PackageScanClassResolver;

/**
 * Annotation based loader for model classes with Bindy annotations.
 */
public class AnnotationModelLoader {

    private PackageScanClassResolver resolver;
    private Set<Class<? extends Annotation>> annotations;

    public AnnotationModelLoader(PackageScanClassResolver resolver) {
        this.resolver = resolver;

        annotations = new LinkedHashSet<Class<? extends Annotation>>();
        annotations.add(CsvRecord.class);
        annotations.add(Link.class);
        annotations.add(Message.class);
    }

    public Set<Class> loadModels(String packageName) throws Exception {
        return resolver.findAnnotated(annotations, packageName);
    }

}
