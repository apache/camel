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
package org.apache.camel.maven.packaging.generics;

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;

public class OwbTypeVariableImpl {
    private static final Class<?>[] TYPE_VARIABLE_TYPES = new Class<?>[] { TypeVariable.class };

    /**
     * Java TypeVariable is different in various JDK versions. Thus it is not possible to e.g. write a custom
     * TypeVariable which works in either Java7 and Java8 as they introduced new methods in Java8 which have return
     * generics which only exist in Java8 :( As workaround we dynamically crate a proxy to wrap this and do the
     * delegation manually. This is of course slower, but as we do not use it often it might not have much impact.
     *
     * @param  typeVariable
     * @param  bounds
     * @return              the typeVariable with the defined bounds.
     */
    public static TypeVariable createTypeVariable(TypeVariable typeVariable, Type... bounds) {

        return (TypeVariable) Proxy.newProxyInstance(OwbTypeVariableImpl.class.getClassLoader(), TYPE_VARIABLE_TYPES,
                new OwbTypeVariableInvocationHandler(typeVariable, bounds));
    }

    public static class OwbTypeVariableInvocationHandler implements InvocationHandler {

        private final String name;
        private final GenericDeclaration genericDeclaration;
        private final Type[] bounds;

        public OwbTypeVariableInvocationHandler(TypeVariable typeVariable, Type... bounds) {
            name = typeVariable.getName();
            genericDeclaration = typeVariable.getGenericDeclaration();
            if (bounds == null || bounds.length == 0) {
                this.bounds = typeVariable.getBounds();
            } else {
                this.bounds = bounds;
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            switch (methodName) {
                case "equals":
                    return typeVariableEquals(args[0]);
                case "hashCode":
                    return typeVariableHashCode();
                case "toString":
                    return typeVariableToString();
                case "getName":
                    return getName();
                case "getGenericDeclaration":
                    return getGenericDeclaration();
                case "getBounds":
                    return getBounds();
            }

            // new method from java8...
            return null;
        }

        /** method from TypeVariable */
        public String getName() {
            return name;
        }

        /** method from TypeVariable */
        public GenericDeclaration getGenericDeclaration() {
            return genericDeclaration;
        }

        /** method from TypeVariable */
        public Type[] getBounds() {
            return bounds.clone();
        }

        /** method from TypeVariable */
        public int typeVariableHashCode() {
            return Arrays.hashCode(bounds) ^ name.hashCode() ^ genericDeclaration.hashCode();
        }

        /** method from TypeVariable */
        public boolean typeVariableEquals(Object object) {
            if (this == object) {
                return true;
            } else if (object instanceof TypeVariable) {
                TypeVariable<?> that = (TypeVariable<?>) object;
                return name.equals(that.getName()) && genericDeclaration.equals(that.getGenericDeclaration())
                        && Arrays.equals(bounds, that.getBounds());
            } else {
                return false;
            }

        }

        /** method from TypeVariable */
        public String typeVariableToString() {
            StringBuilder buffer = new StringBuilder();
            buffer.append(name);
            if (bounds.length > 0) {
                buffer.append(" extends ");
                boolean first = true;
                for (Type bound : bounds) {
                    if (first) {
                        first = false;
                    } else {
                        buffer.append(',');
                    }
                    buffer.append(' ').append(bound);
                }
            }
            return buffer.toString();
        }

    }
}
