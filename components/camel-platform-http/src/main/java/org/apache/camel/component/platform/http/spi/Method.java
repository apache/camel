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
package org.apache.camel.component.platform.http.spi;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * An HTTP method.
 */
public enum Method {
    GET(false), HEAD(false), POST(true), PUT(true), DELETE(false), TRACE(false), OPTIONS(false), CONNECT(false), PATCH(
            true);
    private static final Set<Method> ALL = Collections.unmodifiableSet(new TreeSet<>(Arrays.asList(values())));
    private final boolean canHaveBody;

    private Method(boolean canHaveBody) {
        this.canHaveBody = canHaveBody;
    }

    public static Set<Method> getAll() {
        return ALL;
    }

    /**
     * @return {@code true} if HTTP requests with this {@link Method} can have a body; {@code false} otherwise
     */
    public boolean canHaveBody() {
        return canHaveBody;
    }

    /**
     * Parse the given comma separated {@code methodList} to a {@link Set} of {@link Method}s. If {@code methodList} is
     * empty or {@code null} returns {@link #ALL}.
     *
     * @param methodList a comma separated list of HTTP method names.
     * @return a {@link Set} of {@link Method}s
     */
    public static Set<Method> parseList(String methodList) {
        if (methodList == null) {
            return ALL;
        }
        methodList = methodList.toUpperCase(Locale.ROOT);
        String[] methods = methodList.split(",");
        if (methods.length == 0) {
            return ALL;
        } else if (methods.length == 1) {
            return Collections.singleton(Method.valueOf(methods[0]));
        } else {
            Set<Method> result = new TreeSet<>();
            for (String method : methods) {
                result.add(Method.valueOf(method.trim()));
            }
            return ALL.equals(result) ? ALL : Collections.unmodifiableSet(result);
        }
    }
}
