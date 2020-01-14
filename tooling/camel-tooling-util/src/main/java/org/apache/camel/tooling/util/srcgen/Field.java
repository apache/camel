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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Field {

    String literalInit;
    List<Annotation> annotations = new ArrayList<>();
    Javadoc javadoc = new Javadoc();
    boolean isPrivate;
    boolean isPublic;
    boolean isStatic;
    boolean isFinal;
    String name;
    GenericType type;

    public Field setPublic() {
        isPublic = true;
        isPrivate = false;
        return this;
    }
    public Field setPrivate() {
        isPublic = false;
        isPrivate = true;
        return this;
    }
    public String getName() {
        return null;
    }
    public Field setName(String name) {
        this.name = name;
        return this;
    }
    public GenericType getType() {
        return type;
    }
    public Field setType(Class<?> type) {
        return setType(new GenericType(type));
    }
    public Field setType(GenericType type) {
        this.type = type;
        return this;
    }
    public Field setStatic(boolean isStatic) {
        this.isStatic = isStatic;
        return this;
    }
    public Field setFinal(boolean isFinal) {
        this.isFinal = isFinal;
        return this;
    }
    public String getLiteralInitializer() {
        return literalInit;
    }
    public Field setLiteralInitializer(String init) {
        this.literalInit = init;
        return this;
    }
    public Field setStringInitializer(String init) {
        this.literalInit = Annotation.quote(init);
        return this;
    }
    public Annotation addAnnotation(Class<? extends java.lang.annotation.Annotation> clazz) {
        Annotation ann = new Annotation(clazz);
        this.annotations.add(ann);
        return ann;
    }
    public boolean hasAnnotation(Class<? extends java.lang.annotation.Annotation> clazz) {
        return annotations.stream().anyMatch(a -> Objects.equals(a.getType(), clazz));
    }
    public Annotation getAnnotation(Class<? extends java.lang.annotation.Annotation> clazz) {
        return annotations.stream().filter(a -> Objects.equals(a.getType(), clazz)).findAny().orElse(null);
    }
    public Javadoc getJavaDoc() {
        return javadoc;
    }

}
