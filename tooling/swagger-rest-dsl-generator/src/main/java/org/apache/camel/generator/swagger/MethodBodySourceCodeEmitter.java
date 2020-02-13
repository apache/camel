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
package org.apache.camel.generator.swagger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.squareup.javapoet.MethodSpec;

import static org.apache.commons.lang3.ClassUtils.isPrimitiveOrWrapper;

class MethodBodySourceCodeEmitter implements CodeEmitter<MethodSpec> {

    private final MethodSpec.Builder builder;

    private boolean first = true;

    private final Deque<Integer> indentIntentStack = new ArrayDeque<>();

    private final Deque<Integer> indentStack = new ArrayDeque<>();

    MethodBodySourceCodeEmitter(final MethodSpec.Builder builder) {
        this.builder = builder;
        indentStack.push(0);
    }

    @Override
    public CodeEmitter<MethodSpec> emit(final String method, final Object... args) {
        final boolean hasArgs = args != null && args.length > 0;

        final int indent = indentLevelOf(method);

        if (!first) {
            builder.addCode("\n");
        }

        builder.addCode(String.join("", Collections.nCopies(indentStack.peek(), "$<")));
        builder.addCode(String.join("", Collections.nCopies(indent, "$>")));

        if (!first) {
            builder.addCode(".");
        }

        indentStack.push(indent);

        if (hasArgs) {
            builder.addCode("$L(" + invocationLiteralsFor(args) + ")", extend(method, argumentsFor(args)));
        } else {
            builder.addCode("$L()", method);
        }

        first = false;

        return this;
    }

    @Override
    public MethodSpec result() {
        builder.addCode(String.join("", Collections.nCopies(indentStack.peek(), "$<")));
        builder.addCode(";\n");
        return builder.build();
    }

    Object[] argumentsFor(final Object[] args) {
        final List<Object> arguments = new ArrayList<>(args.length);

        for (final Object arg : args) {
            if (isPrimitiveOrWrapper(arg.getClass())) {
                arguments.add(arg);
            } else if (arg instanceof String) {
                arguments.add(arg);
            } else if (arg instanceof Enum) {
                arguments.add(arg.getClass());
                arguments.add(arg);
            } else if (arg instanceof String[]) {
                arguments.add(Arrays.stream((String[]) arg).collect(Collectors.joining(",")));
            }
        }

        return arguments.toArray(new Object[arguments.size()]);
    }

    Object[] extend(final Object first, final Object... others) {
        if (others == null || others.length == 0) {
            return new Object[] {first};
        }

        final Object[] ret = new Object[1 + others.length];

        ret[0] = first;
        System.arraycopy(others, 0, ret, 1, others.length);

        return ret;
    }

    int indentLevelOf(final String method) {
        switch (method) {
            case "rest":
                return 0;
            case "post":
            case "get":
            case "put":
            case "patch":
            case "delete":
            case "head":
            case "options":
                return 1;
            case "param":
                indentIntentStack.push(3);
                return 2;
            case "endParam":
                indentIntentStack.pop();
                return 2;
            case "route":
                indentIntentStack.push(3);
                return 2;
            case "endRest":
                indentIntentStack.pop();
                return 2;
            default:
                if (indentIntentStack.isEmpty()) {
                    return 2;
                }
                return indentIntentStack.peek();
        }
    }

    String invocationLiteralsFor(final Object[] args) {
        final StringJoiner literals = new StringJoiner(",");

        for (final Object arg : args) {
            if (isPrimitiveOrWrapper(arg.getClass())) {
                literals.add("$L");
            } else if (arg instanceof String) {
                literals.add("$S");
            } else if (arg instanceof Enum) {
                literals.add("$T.$L");
            } else if (arg instanceof String[]) {
                literals.add("$S");
            }
        }

        return literals.toString();
    }

}
