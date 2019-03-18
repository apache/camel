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

import java.lang.annotation.Annotation;
import java.util.List;

import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.Type;
import org.jboss.forge.roaster.model.TypeVariable;
import org.jboss.forge.roaster.model.Visibility;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaDocSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.source.ParameterSource;
import org.jboss.forge.roaster.model.source.TypeVariableSource;

/**
 * In use when we have discovered a RouteBuilder being as anonymous inner class
 */
public class AnonymousMethodSource implements MethodSource<JavaClassSource> {

    // this implementation should only implement the needed logic to support the parser

    private final JavaClassSource origin;
    private final Object internal;

    public AnonymousMethodSource(JavaClassSource origin, Object internal) {
        this.origin = origin;
        this.internal = internal;
    }

    @Override
    public MethodSource<JavaClassSource> setDefault(boolean b) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> setSynchronized(boolean b) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> setNative(boolean b) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> setReturnType(Class<?> aClass) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> setReturnType(String s) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> setReturnType(JavaType<?> javaType) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> setReturnTypeVoid() {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> setBody(String s) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> setConstructor(boolean b) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> setParameters(String s) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> addThrows(String s) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> addThrows(Class<? extends Exception> aClass) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> removeThrows(String s) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> removeThrows(Class<? extends Exception> aClass) {
        return null;
    }

    @Override
    public boolean isSynchronized() {
        return false;
    }

    @Override
    public boolean isNative() {
        return false;
    }

    @Override
    public String getBody() {
        return null;
    }

    @Override
    public boolean isConstructor() {
        return false;
    }

    @Override
    public Type<JavaClassSource> getReturnType() {
        return null;
    }

    @Override
    public boolean isReturnTypeVoid() {
        return false;
    }

    @Override
    public List<ParameterSource<JavaClassSource>> getParameters() {
        return null;
    }

    @Override
    public String toSignature() {
        return null;
    }

    @Override
    public List<String> getThrownExceptions() {
        return null;
    }

    @Override
    public boolean isDefault() {
        return false;
    }

    @Override
    public ParameterSource<JavaClassSource> addParameter(Class<?> aClass, String s) {
        return null;
    }

    @Override
    public ParameterSource<JavaClassSource> addParameter(String s, String s1) {
        return null;
    }

    @Override
    public ParameterSource<JavaClassSource> addParameter(JavaType<?> javaType, String s) {
        return null;
    }

    @Override
    public void removeAllAnnotations() {
    }

    @Override
    public MethodSource<JavaClassSource> removeParameter(ParameterSource<JavaClassSource> parameterSource) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> removeParameter(Class<?> aClass, String s) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> removeParameter(String s, String s1) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> removeParameter(JavaType<?> javaType, String s) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> setAbstract(boolean b) {
        return null;
    }

    @Override
    public List<AnnotationSource<JavaClassSource>> getAnnotations() {
        return null;
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> aClass) {
        return false;
    }

    @Override
    public boolean hasAnnotation(String s) {
        return false;
    }

    @Override
    public AnnotationSource<JavaClassSource> getAnnotation(Class<? extends Annotation> aClass) {
        return null;
    }

    @Override
    public AnnotationSource<JavaClassSource> getAnnotation(String s) {
        return null;
    }

    @Override
    public AnnotationSource<JavaClassSource> addAnnotation() {
        return null;
    }

    @Override
    public AnnotationSource<JavaClassSource> addAnnotation(Class<? extends Annotation> aClass) {
        return null;
    }

    @Override
    public AnnotationSource<JavaClassSource> addAnnotation(String s) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> removeAnnotation(org.jboss.forge.roaster.model.Annotation<JavaClassSource> annotation) {
        return null;
    }

    @Override
    public List<TypeVariableSource<JavaClassSource>> getTypeVariables() {
        return null;
    }

    @Override
    public TypeVariableSource<JavaClassSource> getTypeVariable(String s) {
        return null;
    }

    @Override
    public TypeVariableSource<JavaClassSource> addTypeVariable() {
        return null;
    }

    @Override
    public TypeVariableSource<JavaClassSource> addTypeVariable(String s) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> removeTypeVariable(String s) {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> removeTypeVariable(TypeVariable<?> typeVariable) {
        return null;
    }

    @Override
    public boolean hasJavaDoc() {
        return false;
    }

    @Override
    public boolean isAbstract() {
        return false;
    }

    @Override
    public MethodSource<JavaClassSource> setFinal(boolean b) {
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
    public JavaDocSource<MethodSource<JavaClassSource>> getJavaDoc() {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> removeJavaDoc() {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> setName(String s) {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public JavaClassSource getOrigin() {
        return origin;
    }

    @Override
    public MethodSource<JavaClassSource> setStatic(boolean b) {
        return null;
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public MethodSource<JavaClassSource> setPackagePrivate() {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> setPublic() {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> setPrivate() {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> setProtected() {
        return null;
    }

    @Override
    public MethodSource<JavaClassSource> setVisibility(Visibility visibility) {
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

    @Override
    public boolean hasTypeVariable(String arg0) {
        return false;
    }

    @Override
    public MethodSource<JavaClassSource> setReturnType(Type<?> arg0) {
        return null;
    }
}
