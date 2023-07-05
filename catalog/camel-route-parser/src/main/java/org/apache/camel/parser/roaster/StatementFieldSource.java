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
package org.apache.camel.parser.roaster;

import java.util.List;

import org.jboss.forge.roaster.model.Annotation;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.Type;
import org.jboss.forge.roaster.model.Visibility;
import org.jboss.forge.roaster.model.impl.TypeImpl;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaDocSource;
import org.jboss.forge.roaster.model.source.JavaSource;

public class StatementFieldSource<O extends JavaSource<O>> implements FieldSource<O> {

    // this implementation should only implement the needed logic to support the parser

    private final O origin;
    private final Object internal;
    private final Type<O> type;

    public StatementFieldSource(O origin, Object internal, Object typeInternal) {
        this.origin = origin;
        this.internal = internal;
        this.type = new TypeImpl<>(origin, typeInternal);
    }

    @Override
    public FieldSource<O> setType(Class clazz) {
        return null;
    }

    @Override
    public FieldSource<O> setType(String type) {
        return null;
    }

    @Override
    public FieldSource<O> setLiteralInitializer(String value) {
        return null;
    }

    @Override
    public FieldSource<O> setStringInitializer(String value) {
        return null;
    }

    @Override
    public FieldSource<O> setTransient(boolean value) {
        return null;
    }

    @Override
    public FieldSource<O> setVolatile(boolean value) {
        return null;
    }

    @Override
    public FieldSource<O> setType(JavaType entity) {
        return null;
    }

    @Override
    public List<AnnotationSource<O>> getAnnotations() {
        return null;
    }

    @Override
    public boolean hasAnnotation(String type) {
        return false;
    }

    @Override
    public boolean hasAnnotation(Class<? extends java.lang.annotation.Annotation> type) {
        return false;
    }

    @Override
    public AnnotationSource<O> getAnnotation(String type) {
        return null;
    }

    @Override
    public AnnotationSource<O> addAnnotation() {
        return null;
    }

    @Override
    public AnnotationSource<O> addAnnotation(String className) {
        return null;
    }

    @Override
    public void removeAllAnnotations() {
    }

    @Override
    public FieldSource<O> removeAnnotation(Annotation annotation) {
        return null;
    }

    @Override
    public AnnotationSource<O> addAnnotation(Class type) {
        return null;
    }

    @Override
    public AnnotationSource<O> getAnnotation(Class type) {
        return null;
    }

    @Override
    public Type<O> getType() {
        return type;
    }

    @Override
    public String getStringInitializer() {
        return null;
    }

    @Override
    public String getLiteralInitializer() {
        return null;
    }

    @Override
    public boolean isTransient() {
        return false;
    }

    @Override
    public boolean isVolatile() {
        return false;
    }

    @Override
    public FieldSource<O> setFinal(boolean finl) {
        return null;
    }

    @Override
    public boolean isFinal() {
        return false;
    }

    @Override
    public Object getInternal() {
        return internal;
    }

    @Override
    public JavaDocSource<FieldSource<O>> getJavaDoc() {
        return null;
    }

    @Override
    public boolean hasJavaDoc() {
        return false;
    }

    @Override
    public FieldSource<O> removeJavaDoc() {
        return null;
    }

    @Override
    public FieldSource<O> setName(String name) {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public O getOrigin() {
        return origin;
    }

    @Override
    public FieldSource<O> setStatic(boolean value) {
        return null;
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public FieldSource<O> setPackagePrivate() {
        return null;
    }

    @Override
    public FieldSource<O> setPublic() {
        return null;
    }

    @Override
    public FieldSource<O> setPrivate() {
        return null;
    }

    @Override
    public FieldSource<O> setProtected() {
        return null;
    }

    @Override
    public FieldSource<O> setVisibility(Visibility scope) {
        return null;
    }

    @Override
    public boolean isPackagePrivate() {
        return false;
    }

    @Override
    public boolean isPublic() {
        return false;
    }

    @Override
    public boolean isPrivate() {
        return false;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public Visibility getVisibility() {
        return null;
    }

    @Override
    public int getColumnNumber() {
        return 0;
    }

    @Override
    public int getStartPosition() {
        return 0;
    }

    @Override
    public int getEndPosition() {
        return 0;
    }

    @Override
    public int getLineNumber() {
        return 0;
    }
}
