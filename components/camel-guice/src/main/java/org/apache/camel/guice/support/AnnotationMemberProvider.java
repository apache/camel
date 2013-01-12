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
package org.apache.camel.guice.support;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.google.inject.TypeLiteral;

/**
 * A provider of an annotation based injection point which can use the value of
 * an annotation together with the member on which the annotation is placed to
 * determine the value.
 * 
 * @version
 */
public interface AnnotationMemberProvider<A extends Annotation> {

    /** Returns the value to be injected for the given annotated field */
    Object provide(A annotation, TypeLiteral<?> type, Field field);

    /**
     * Returns the value to be injected for the given annotated method parameter
     * value
     */
    Object provide(A annotation, TypeLiteral<?> type, Method method,
            Class<?> parameterType, int parameterIndex);

    /** Returns true if the given parameter on the annotated method can be null */
    boolean isNullParameterAllowed(A annotation, Method method,
            Class<?> parameterType, int parameterIndex);
}
