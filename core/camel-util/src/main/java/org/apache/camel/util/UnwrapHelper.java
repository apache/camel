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

package org.apache.camel.util;

import java.lang.reflect.Method;

/**
 * Helper for unwrapping from the proxy objects
 */
public final class UnwrapHelper {

    public static <T> T unwrapClientProxy(T obj) {
        try {
            Method method =
                    ReflectionHelper.findMethod(Class.forName("io.quarkus.arc.ClientProxy"), "unwrap", Object.class);
            if (method != null) {
                return (T) method.invoke(null, obj);
            }
        } catch (Exception e) {
        }
        return null;
    }
}
