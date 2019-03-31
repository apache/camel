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
import java.util.List;

/**
 * Interface for proxy methods.
 */
public interface ApiMethod {

    /**
     * Returns method name.
     * @return name
     */
    String getName();

    /**
     * Returns method result type.
     * @return result type
     */
    Class<?> getResultType();

    /**
     * Returns method argument names.
     * @return argument names
     */
    List<String> getArgNames();

    /**
     * Return method argument types.
     * @return argument types
     */
    List<Class<?>> getArgTypes();

    /**
     * Returns {@link Method} in proxy type.
     * @return method
     */
    Method getMethod();
}
