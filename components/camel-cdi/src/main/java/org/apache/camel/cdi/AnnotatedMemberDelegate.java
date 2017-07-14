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
package org.apache.camel.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.util.Set;
import javax.enterprise.inject.spi.AnnotatedMember;
import javax.enterprise.inject.spi.AnnotatedType;

class AnnotatedMemberDelegate<T> extends AnnotatedDelegate implements AnnotatedMember<T> {

    private final AnnotatedMember<T> delegate;
    
    AnnotatedMemberDelegate(AnnotatedMember<T> delegate, Set<Annotation> annotations) {
        super(delegate, annotations);
        this.delegate = delegate;
    }

    @Override
    public AnnotatedType<T> getDeclaringType() {
        return delegate.getDeclaringType();
    }

    @Override
    public Member getJavaMember() {
        return delegate.getJavaMember();
    }

    @Override
    public boolean isStatic() {
        return delegate.isStatic();
    }
}
