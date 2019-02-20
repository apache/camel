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
package org.apache.camel.maven.packaging.srcgen;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Method {
    String name;
    GenericType returnType;
    boolean isPublic;
    boolean isConstructor;
    String body;
    List<Param> parameters = new ArrayList<>();
    List<GenericType> exceptions = new ArrayList<>();
    List<Annotation> annotations = new ArrayList<>();
    Javadoc javadoc = new Javadoc();

    public Method setPublic() {
        isPublic = true;
        return this;
    }

    public String getName() {
        return name;
    }
    public Method setName(String name) {
        this.name = name;
        return this;
    }

    public GenericType getReturnType() {
        return returnType;
    }
    public Method setReturnType(Type returnType) {
        return setReturnType(new GenericType(returnType));
    }
    public Method setReturnType(GenericType returnType) {
        this.returnType = returnType;
        return this;
    }

    public Method addParameter(Class<?> type, String name) {
        return addParameter(new GenericType(type), name);
    }
    public Method addParameter(GenericType type, String name) {
        this.parameters.add(new Param(type, name));
        return this;
    }

    public String getBody() {
        return body;
    }
    public Method setBody(String body) {
        this.body = body;
        return this;
    }

    public Method addThrows(Class<?> type) {
        return addThrows(new GenericType(type));
    }
    public Method addThrows(GenericType type) {
        this.exceptions.add(type);
        return this;
    }

    public Annotation addAnnotation(Class<? extends java.lang.annotation.Annotation> clazz) {
        Annotation ann = new Annotation(clazz);
        this.annotations.add(ann);
        return ann;
    }
    public boolean hasAnnotation(Class<? extends java.lang.annotation.Annotation> clazz) {
        return annotations.stream().map(Annotation::getType).anyMatch(clazz::equals);
    }
    public Annotation getAnnotation(Class<? extends java.lang.annotation.Annotation> clazz) {
        return annotations.stream().filter(a -> Objects.equals(a.getType(), clazz)).findAny().orElse(null);
    }

    public Javadoc getJavaDoc() {
        return javadoc;
    }

    public Method setConstructor(boolean cns) {
        this.isConstructor = cns;
        return this;
    }

    public boolean hasJavaDoc() {
        return javadoc.text != null;
    }
}
