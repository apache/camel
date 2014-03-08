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
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import com.google.inject.TypeLiteral;

/**
 * A useful base class for implementors meaning they only have to implement a
 * single method whether a Field or Method parameter is being injected
 * 
 * @version
 */
public abstract class AnnotationMemberProviderSupport<A extends Annotation>
        implements AnnotationMemberProvider<A> {

    public Object provide(A annotation, TypeLiteral<?> type, Field field) {
        TypeLiteral<?> requiredType = type.getFieldType(field);
        return provide(annotation, field, requiredType, field.getType(), null);
    }

    public Object provide(A annotation, TypeLiteral<?> type, Method method,
            Class<?> parameterType, int parameterIndex) {

        TypeLiteral<?> requiredType = type.getParameterTypes(method).get(
                parameterIndex);
        Annotation[] annotations = method.getParameterAnnotations()[parameterIndex];
        return provide(annotation, method, requiredType,
                method.getParameterTypes()[parameterIndex], annotations);
    }

    /**
     * The default method to create a value for the named member of the
     * requested type
     */
    protected abstract Object provide(A annotation, Member member,
            TypeLiteral<?> requiredType, Class<?> memberType,
            Annotation[] annotations);
}
