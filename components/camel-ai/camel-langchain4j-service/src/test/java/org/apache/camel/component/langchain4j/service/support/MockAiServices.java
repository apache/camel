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
package org.apache.camel.component.langchain4j.service.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/**
 * Builds the JDK proxies that stand in for an AI Service where the subject is the component's own introspection -
 * method resolution, annotation validation, parameter binding - and not LangChain4j.
 * <p>
 * For anything that depends on what LangChain4j actually generates or renders, build a real
 * {@code AiServices.builder(...)} against a {@link RecordingChatModel} instead: a proxy written here cannot show that a
 * template was substituted, only that a method was called.
 */
public final class MockAiServices {

    private MockAiServices() {
    }

    /**
     * A proxy answering {@code "response from <method name>"}, so a test can tell which method was selected.
     *
     * @param  serviceInterface the business interface the proxy exposes
     * @param  extraInterfaces  further interfaces the proxy implements, as a framework-built proxy would
     * @return                  the proxy
     */
    public static <T> T create(Class<T> serviceInterface, Class<?>... extraInterfaces) {
        return create(serviceInterface, (proxy, method, args) -> "response from " + method.getName(), extraInterfaces);
    }

    /**
     * A proxy whose business methods run the given handler, for tests that capture the bound arguments.
     *
     * @param  serviceInterface the business interface the proxy exposes
     * @param  handler          invoked for every method that is not declared by {@link Object}
     * @param  extraInterfaces  further interfaces the proxy implements
     * @return                  the proxy
     */
    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> serviceInterface, InvocationHandler handler, Class<?>... extraInterfaces) {
        Class<?>[] interfaces = new Class<?>[extraInterfaces.length + 1];
        interfaces[0] = serviceInterface;
        System.arraycopy(extraInterfaces, 0, interfaces, 1, extraInterfaces.length);
        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                interfaces,
                (proxy, method, args) -> {
                    // Answer the Object methods sanely, so a registry lookup or a log statement never gets a String
                    // back from hashCode()
                    if (method.getDeclaringClass() == Object.class) {
                        switch (method.getName()) {
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            default:
                                return "mock " + serviceInterface.getSimpleName();
                        }
                    }
                    return handler.invoke(proxy, method, args);
                });
    }
}
