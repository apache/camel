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
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaDocSource;

public class StatementFieldSource implements FieldSource {

    // this implementation should only implement the needed logic to support the parser

    private final JavaClassSource origin;
    private final Object internal;
    private final Type type;

    public StatementFieldSource(JavaClassSource origin, Object internal, Object typeInternal) {
        this.origin = origin;
        this.internal = internal;
        this.type = new TypeImpl(origin, typeInternal);
    }

    @Override
    public FieldSource setType(Class clazz) {
        return null;
    }

    @Override
    public FieldSource setType(String type) {
        return null;
    }

    @Override
    public FieldSource setLiteralInitializer(String value) {
        return null;
    }

    @Override
    public FieldSource setStringInitializer(String value) {
        return null;
    }

    @Override
    public FieldSource setTransient(boolean value) {
        return null;
    }

    @Override
    public FieldSource setVolatile(boolean value) {
        return null;
    }

    @Override
    public FieldSource setType(JavaType entity) {
        return null;
    }

    @Override
    public List<AnnotationSource> getAnnotations() {
        return null;
    }

    @Override
    public boolean hasAnnotation(String type) {
        return false;
    }

    @Override
    public boolean hasAnnotation(Class type) {
        return false;
    }

    @Override
    public AnnotationSource getAnnotation(String type) {
        return null;
    }

    @Override
    public AnnotationSource addAnnotation() {
        return null;
    }

    @Override
    public AnnotationSource addAnnotation(String className) {
        return null;
    }

    @Override
    public void removeAllAnnotations() {
    }

    @Override
    public Object removeAnnotation(Annotation annotation) {
        return null;
    }

    @Override
    public AnnotationSource addAnnotation(Class type) {
        return null;
    }

    @Override
    public AnnotationSource getAnnotation(Class type) {
        return null;
    }

    @Override
    public Type getType() {
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
    public Object setFinal(boolean finl) {
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
    public JavaDocSource getJavaDoc() {
        return null;
    }

    @Override
    public boolean hasJavaDoc() {
        return false;
    }

    @Override
    public Object removeJavaDoc() {
        return null;
    }

    @Override
    public Object setName(String name) {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public Object getOrigin() {
        return origin;
    }

    @Override
    public Object setStatic(boolean value) {
        return null;
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public Object setPackagePrivate() {
        return null;
    }

    @Override
    public Object setPublic() {
        return null;
    }

    @Override
    public Object setPrivate() {
        return null;
    }

    @Override
    public Object setProtected() {
        return null;
    }

    @Override
    public Object setVisibility(Visibility scope) {
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
