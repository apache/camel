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
package org.apache.camel.component.spark.annotations;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

import org.apache.spark.api.java.AbstractJavaRDDLike;

import static org.apache.camel.util.ObjectHelper.findMethodsWithAnnotation;

public final class AnnotatedRddCallback {

    private AnnotatedRddCallback() {
    }

    public static org.apache.camel.component.spark.RddCallback annotatedRddCallback(final Object callback) {
        final List<Method> rddCallbacks = findMethodsWithAnnotation(callback.getClass(), RddCallback.class);
        if (rddCallbacks.size() > 0) {
            return new org.apache.camel.component.spark.RddCallback() {
                @Override
                public Object onRdd(AbstractJavaRDDLike rdd, Object... payloads) {
                    try {
                        List<Object> arguments = new ArrayList<>(payloads.length + 1);
                        arguments.add(rdd);
                        arguments.addAll(asList(payloads));
                        if (arguments.get(1) == null) {
                            arguments.remove(1);
                        }

                        Method callbackMethod = rddCallbacks.get(0);
                        callbackMethod.setAccessible(true);
                        return callbackMethod.invoke(callback, arguments.toArray(new Object[arguments.size()]));
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        }
        throw new UnsupportedOperationException("Can't find methods annotated with @Rdd.");
    }

}
