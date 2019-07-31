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
package org.apache.camel.support.component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Delegate class for {@link ApiMethod}.
 * This class is instantiated by Enumerations for Api Proxy types.
 * <p>
 *     For example:
 * </p>
 * <pre>
 * {@code
 *     public enum HelloWorldMethod implements ApiMethod {
 *         SAYHI(String.class, "sayHi", ApiMethodArg.from(String.class, "name");
 *
 *         private ApiMethodImpl apiMethod;
 *
 *         private HelloWorldMethods(Class<?> resultType, String name, ApiMethodArg... args) throws IllegalArgumentException {
 *             this.apiMethod = new ApiMethod(HelloWorld.class, resultType, name, args);
 *         }
 *
 *         // implement ApiMethod interface
 *         String getName() { return apiMethod.getName(); }
 *         Class<?> getResultType() {return apiMethod.getResultType(); }
 *         List<String> getArgNames() { return apiMethod.getArgNames(); }
 *         List<Class<?>> getArgTypes() {return apiMethod.getArgTypes(); }
 *         Method getMethod() { return apiMethod.getMethod(); }
 *     }
 * }
 * </pre>
 */
public final class ApiMethodImpl implements ApiMethod {

    // name, result class, ordered argument names and classes, and Method to invoke
    private final String name;
    private final Class<?> resultType;
    private final List<String> argNames;
    private final List<Class<?>> argTypes;
    private final Method method;

    public ApiMethodImpl(Class<?> proxyType, Class<?> resultType, String name, ApiMethodArg... args) throws IllegalArgumentException {
        this.name = name;
        this.resultType = resultType;

        final List<String> tmpArgNames = new ArrayList<>(args.length);
        final List<Class<?>> tmpArgTypes = new ArrayList<>(args.length);
        for (ApiMethodArg arg : args) {
            tmpArgTypes.add(arg.getType());
            tmpArgNames.add(arg.getName());
        }

        this.argNames = Collections.unmodifiableList(tmpArgNames);
        this.argTypes = Collections.unmodifiableList(tmpArgTypes);

        // find method in Proxy type
        try {
            this.method = proxyType.getMethod(name, argTypes.toArray(new Class[args.length]));
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                String.format("Missing method %s %s", name, argTypes.toString().replace('[', '(').replace(']', ')')),
                e);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Class<?> getResultType() {
        return resultType;
    }

    @Override
    public List<String> getArgNames() {
        return argNames;
    }

    @Override
    public List<Class<?>> getArgTypes() {
        return argTypes;
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("{")
            .append("name=").append(name)
            .append(", resultType=").append(resultType)
            .append(", argNames=").append(argNames)
            .append(", argTypes=").append(argTypes)
            .append("}");

        return builder.toString();
    }
}
