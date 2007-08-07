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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class ReflectionUtils extends org.springframework.util.ReflectionUtils {
    public static <T extends Annotation> void callLifecycleMethod(final Object bean, final Class<T> annotation) {
        ReflectionUtils.doWithMethods(bean.getClass(), new ReflectionUtils.MethodCallback() {
            public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
                if (method.getAnnotation(annotation) != null) {
                    try {
                        method.invoke(bean, (Object[])null);
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalStateException("Failure to invoke " + method + " on " + bean.getClass() + ": args=[]", ex);
                    } catch (IllegalAccessException ex) {
                        throw new UnsupportedOperationException(ex.toString());
                    } catch (InvocationTargetException ex) {
                        throw new UnsupportedOperationException("PostConstruct method on bean threw exception", ex.getTargetException());
                    }
                }
            }
        });
    }

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
            throw new UnsupportedOperationException("Cannot inject value of class '" + value.getClass() + "' into " + f);
        } catch (IllegalAccessException ex) {
            ReflectionUtils.handleReflectionException(ex);
        }
    }
}
