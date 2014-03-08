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
package org.apache.camel.guice.jsr250;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.annotation.PreDestroy;

import org.apache.camel.guice.support.Closer;

/**
 * Supports the {@link javax.annotation.PreDestroy} annotation lifecycle from
 * JSR250.
 * <p>
 * To install this closer you need to register the {@link Jsr250Module} in your
 * injector.
 * 
 * @version
 */
public class PreDestroyCloser implements Closer {

    private AnnotatedMethodCache methodCache = new AnnotatedMethodCache(
            PreDestroy.class);

    public void close(Object object) throws Throwable {
        Class<? extends Object> type = object.getClass();
        Method method = methodCache.getMethod(type);
        if (method != null) {
            if (method != null) {
                try {
                    method.invoke(object);
                } catch (InvocationTargetException e) {
                    throw e.getTargetException();
                }
            }
        }
    }
}