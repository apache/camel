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

import org.apache.camel.spi.PackageScanFilter;
import org.apache.camel.util.ObjectHelper;

/**
 * Package scan filter for testing if a given class is annotated with a certain annotation.
 */
public class AnnotatedWithPackageScanFilter implements PackageScanFilter {

    private Class<? extends Annotation> annotation;
    private boolean checkMetaAnnotations;

    public AnnotatedWithPackageScanFilter(Class<? extends Annotation> annotation) {
        this(annotation, false);
    }

    public AnnotatedWithPackageScanFilter(Class<? extends Annotation> annotation, boolean checkMetaAnnotations) {
        this.annotation = annotation;
        this.checkMetaAnnotations = checkMetaAnnotations;
    }

    @Override
    public boolean matches(Class<?> type) {
        return type != null && ObjectHelper.hasAnnotation(type, annotation, checkMetaAnnotations);
    }

    @Override
    public String toString() {
        return "annotated with @" + annotation.getSimpleName();
    }
}
