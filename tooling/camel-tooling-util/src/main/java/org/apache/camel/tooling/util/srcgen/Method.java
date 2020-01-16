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
package org.apache.camel.tooling.util.srcgen;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Method {
    String name;
    GenericType returnType;
    String returnTypeLiteral;
    boolean isDefault;
    boolean isPublic;
    boolean isProtected;
    boolean isPrivate;
    boolean isStatic;
    boolean isConstructor;
    boolean isAbstract;
    String signature;
    String body;
    List<Param> parameters = new ArrayList<>();
    List<String> exceptions = new ArrayList<>();
    List<Annotation> annotations = new ArrayList<>();
    Javadoc javadoc = new Javadoc();

    public Method setSignature(String signature) {
        this.signature = signature;
        return this;
    }

    public Method setPublic() {
        isPublic = true;
        isProtected = false;
        isPrivate = false;
        return this;
    }

    public Method setProtected() {
        isPublic = false;
        isProtected = true;
        isPrivate = false;
        return this;
    }

    public Method setPrivate() {
        isPublic = false;
        isProtected = false;
        isPrivate = true;
        return this;
    }

    public Method setStatic() {
        isStatic = true;
        isDefault = false;
        return this;
    }

    public Method setDefault() {
        isDefault = true;
        isStatic = false;
        return this;
    }

    public Method setAbstract() {
        isAbstract = true;
        return this;
    }

    public String getName() {
        return name;
    }
    public Method setName(String name) {
        this.name = name;
        return this;
    }

    public String getReturnTypeLiteral() {
        return returnTypeLiteral;
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
    public Method setReturnType(String returnType) {
        this.returnType = null;
        this.returnTypeLiteral = returnType;
        return this;
    }


    public Method addParameter(String type, String name) {
        return addParameter(type, name, false);
    }
    public Method addParameter(String type, String name, boolean vararg) {
        this.parameters.add(new Param(type, name, vararg));
        return this;
    }
    public Method addParameter(Class<?> type, String name) {
        return addParameter(new GenericType(type), name);
    }
    public Method addParameter(Class<?> type, String name, boolean vararg) {
        return addParameter(new GenericType(type), name, vararg);
    }
    public Method addParameter(GenericType type, String name) {
        this.parameters.add(new Param(type, name));
        return this;
    }
    public Method addParameter(GenericType type, String name, boolean vararg) {
        this.parameters.add(new Param(type, name, vararg));
        return this;
    }

    public List<Param> getParameters() {
        return this.parameters;
    }
    public List<String> getParametersNames() {
        return this.parameters.stream().map(Param::getName).collect(Collectors.toList());
    }

    public String getBody() {
        return body;
    }
    public Method setBody(String body) {
        this.body = body;
        return this;
    }
    public Method setBodyF(String format, Object... args) {
        this.body = String.format(format, args);
        return this;
    }

    public Method setBody(String... statements) {
        this.body = Stream.of(statements).collect(Collectors.joining("\n"));
        return this;
    }

    public Method addThrows(Class<?> type) {
        return addThrows(new GenericType(type));
    }
    public Method addThrows(GenericType type) {
        return addThrows(type.toString());
    }
    public Method addThrows(String type) {
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

    public Method copy() {
        Method m = new Method();
        m.name = this.name;
        m.returnType = this.returnType;
        m.isDefault = this.isDefault;
        m.isPublic = this.isPublic;
        m.isProtected = this.isProtected;
        m.isPrivate = this.isPrivate;
        m.isStatic = this.isStatic;
        m.isConstructor = this.isConstructor;
        m.isAbstract = this.isAbstract;
        m.signature = this.signature;
        m.body = this.body;
        m.javadoc = this.javadoc;
        m.parameters = new ArrayList<>(this.parameters);
        m.exceptions = new ArrayList<>(this.exceptions);
        m.annotations = new ArrayList<>(this.annotations);

        return m;
    }
}