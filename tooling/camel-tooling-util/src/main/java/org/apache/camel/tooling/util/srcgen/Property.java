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

import org.apache.camel.tooling.util.Strings;

public class Property {

    GenericType type;
    String name;
    Field field;
    Method accessor;
    Method mutator;

    public Property(GenericType type, String name) {
        this.type = type;
        this.name = name;
        field = new Field().setPrivate().setType(type).setName(name);
        accessor = new Method().setPublic().setName("get" + Strings.capitalize(name))
                .setReturnType(type)
                .setBody("return " + name + ";\n");
        mutator = new Method().setPublic().setName("set" + Strings.capitalize(name))
                .addParameter(type, name)
                .setReturnType(void.class)
                .setBody("this." + name + " = " + name + ";\n");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public GenericType getType() {
        return type;
    }

    public void setType(GenericType type) {
        this.type = type;
    }

    public Field getField() {
        return field;
    }

    public Method getAccessor() {
        return accessor;
    }

    public Method getMutator() {
        return mutator;
    }

    public void removeAccessor() {
        accessor = null;
    }

    public void removeMutator() {
        mutator = null;
    }

    public void removeField() {
        field = null;
    }

    public boolean isMutable() {
        return mutator != null;
    }

    public boolean hasField() {
        return field != null;
    }

    public boolean hasAnnotation(Class<? extends java.lang.annotation.Annotation> clazz) {
        return field != null && field.hasAnnotation(clazz)
                || accessor != null && accessor.hasAnnotation(clazz)
                || mutator != null && mutator.hasAnnotation(clazz);
    }

    public Annotation getAnnotation(Class<? extends java.lang.annotation.Annotation> clazz) {
        if (field != null && field.hasAnnotation(clazz)) {
            return field.getAnnotation(clazz);
        } else if (accessor != null && accessor.hasAnnotation(clazz)) {
            return accessor.getAnnotation(clazz);
        } else if (mutator != null && mutator.hasAnnotation(clazz)) {
            return mutator.getAnnotation(clazz);
        } else {
            return null;
        }
    }

}
