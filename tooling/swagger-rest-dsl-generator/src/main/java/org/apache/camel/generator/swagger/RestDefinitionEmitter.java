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
package org.apache.camel.generator.swagger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.model.rest.RestsDefinition;

class RestDefinitionEmitter implements CodeEmitter<RestsDefinition> {

    private final RestsDefinition definition;

    private Object variable;

    RestDefinitionEmitter(final CamelContext context) {
        definition = new RestsDefinition();
        variable = definition;
    }

    @Override
    public CodeEmitter<RestsDefinition> emit(final String method, final Object... args) {
        try {
            final Class<? extends Object> type = variable.getClass();

            final Object[] arguments = argumentsFor(args);

            final Method declaredMethod = type.getMethod(method, parameterTypesOf(arguments));

            variable = declaredMethod.invoke(variable, arguments);
        } catch (final Throwable e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new IllegalStateException(e);
            }
        }

        return this;
    }

    @Override
    public RestsDefinition result() {
        return definition;
    }

    Object[] argumentsFor(final Object[] args) {
        final List<Object> arguments = new ArrayList<>(args.length);

        for (final Object arg : args) {
            if (arg instanceof String[]) {
                arguments.add(Arrays.stream((String[]) arg).collect(Collectors.joining(",")));
            } else {
                arguments.add(arg);
            }
        }

        return arguments.toArray(new Object[arguments.size()]);
    }

    Class<?>[] parameterTypesOf(final Object[] args) {
        final Class<?>[] parameterTypes = new Class<?>[args.length];

        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = args[i].getClass();
        }

        return parameterTypes;
    }

    Class<?>[] typesOf(final Object[] args) {
        final Class<?>[] types = new Class<?>[args.length];

        for (int i = 0; i < types.length; i++) {
            types[i] = args[i].getClass();
        }

        return types;
    }

}
