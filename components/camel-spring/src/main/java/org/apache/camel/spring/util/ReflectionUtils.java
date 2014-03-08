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
package org.apache.camel.spring.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Reflection utilities, extending Spring ReflectionUtils.
 */
public class ReflectionUtils extends org.springframework.util.ReflectionUtils {

    public static void setField(Field f, Object instance, Object value) {
        try {
            boolean oldAccessible = f.isAccessible();
            boolean shouldSetAccessible = !Modifier.isPublic(f.getModifiers()) && !oldAccessible;
            if (shouldSetAccessible) {
                f.setAccessible(true);
            }
            f.set(instance, value);
            if (shouldSetAccessible) {
                f.setAccessible(oldAccessible);
            }
        } catch (IllegalArgumentException ex) {
            throw new UnsupportedOperationException("Cannot inject value of class: " + value.getClass() + " into: " + f);
        } catch (IllegalAccessException ex) {
            handleReflectionException(ex);
        }
    }

}
