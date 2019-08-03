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
package org.apache.camel.impl.scan;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.apache.camel.spi.PackageScanFilter;
import org.apache.camel.util.ObjectHelper;

/**
 * Package scan filter for testing if a given class is annotated with any of the annotations.
 */
public class AnnotatedWithAnyPackageScanFilter implements PackageScanFilter {
    private Set<Class<? extends Annotation>> annotations;
    private boolean checkMetaAnnotations;

    public AnnotatedWithAnyPackageScanFilter(Set<Class<? extends Annotation>> annotations) {
        this(annotations, false);
    }

    public AnnotatedWithAnyPackageScanFilter(Set<Class<? extends Annotation>> annotations, boolean checkMetaAnnotations) {
        this.annotations = annotations;
        this.checkMetaAnnotations = checkMetaAnnotations;
    }

    @Override
    public boolean matches(Class<?> type) {
        if (type == null) {
            return false;
        }
        for (Class<? extends Annotation> annotation : annotations) {
            if (ObjectHelper.hasAnnotation(type, annotation, checkMetaAnnotations)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "annotated with any @[" + annotations + "]";
    }
}
