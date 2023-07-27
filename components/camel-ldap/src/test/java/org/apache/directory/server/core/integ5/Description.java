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
package org.apache.directory.server.core.integ5;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

public class Description {

    private final String displayName;
    private final AnnotatedElement annotated;

    public Description(Method method) {
        annotated = method;
        displayName = String.format("%s(%s)", method.getName(), method.getDeclaringClass().getName());
    }

    public Description(Class<?> clazz) {
        annotated = clazz;
        displayName = clazz.getName();
    }

    public <A extends Annotation> A getAnnotation(Class<A> annotation) {
        return annotated.getAnnotation(annotation);
    }

    public String getDisplayName() {
        return displayName;
    }
}
