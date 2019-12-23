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
package org.apache.camel.component.spark.annotations;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.component.spark.RddCallback;
import org.apache.spark.api.java.JavaRDDLike;

import static java.util.Arrays.asList;
import static org.apache.camel.support.ObjectHelper.invokeMethodSafe;
import static org.apache.camel.util.ObjectHelper.findMethodsWithAnnotation;

class AnnotatedRddCallbackProxy implements RddCallback {

    private final Object objectWithCallback;

    private final List<Method> rddCallbacks;

    private final CamelContext camelContext;

    AnnotatedRddCallbackProxy(Object objectWithCallback, CamelContext camelContext) {
        this.objectWithCallback = objectWithCallback;
        this.camelContext = camelContext;
        this.rddCallbacks = findMethodsWithAnnotation(objectWithCallback.getClass(), org.apache.camel.component.spark.annotations.RddCallback.class);
        if (rddCallbacks.size() == 0) {
            throw new UnsupportedOperationException("Can't find methods annotated with @RddCallback.");
        }
    }

    AnnotatedRddCallbackProxy(Object objectWithCallback) {
        this(objectWithCallback, null);
    }

    @Override
    public Object onRdd(JavaRDDLike rdd, Object... payloads) {
        try {
            List<Object> arguments = new ArrayList<>(payloads.length + 1);
            arguments.add(rdd);
            arguments.addAll(asList(payloads));
            if (arguments.get(1) == null) {
                arguments.remove(1);
            }

            Method callbackMethod = rddCallbacks.get(0);

            if (camelContext != null) {
                for (int i = 1; i < arguments.size(); i++) {
                    arguments.set(i, camelContext.getTypeConverter().convertTo(callbackMethod.getParameterTypes()[i], arguments.get(i)));
                }
            }

            Object[] args = arguments.toArray(new Object[arguments.size()]);
            return invokeMethodSafe(callbackMethod, objectWithCallback, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

}
