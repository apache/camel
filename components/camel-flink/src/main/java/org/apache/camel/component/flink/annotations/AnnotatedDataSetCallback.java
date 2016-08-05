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
package org.apache.camel.component.flink.annotations;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.flink.api.java.DataSet;

import static org.apache.camel.util.ObjectHelper.findMethodsWithAnnotation;

/**
 * Provides facade for working with annotated DataSet callbacks i.e. POJO classes with an appropriate annotations on
 * selected methods.
 */
public class AnnotatedDataSetCallback implements org.apache.camel.component.flink.DataSetCallback {

    private final Object objectWithCallback;

    private final List<Method> dataSetCallbacks;

    private final CamelContext camelContext;

    public AnnotatedDataSetCallback(Object objectWithCallback, CamelContext camelContext) {
        this.objectWithCallback = objectWithCallback;
        this.camelContext = camelContext;
        this.dataSetCallbacks = findMethodsWithAnnotation(objectWithCallback.getClass(), DataSetCallback.class);
        if (dataSetCallbacks.size() == 0) {
            throw new UnsupportedOperationException("Can't find methods annotated with @DataSetCallback");
        }
    }

    public AnnotatedDataSetCallback(Object objectWithCallback) {
        this(objectWithCallback, null);
    }

    @Override
    public Object onDataSet(DataSet ds, Object... payloads) {
        try {
            List<Object> arguments = new ArrayList<>(payloads.length + 1);
            arguments.add(ds);
            arguments.addAll(Arrays.asList(payloads));
            if (arguments.get(1) == null) {
                arguments.remove(1);
            }

            Method callbackMethod = dataSetCallbacks.get(0);
            callbackMethod.setAccessible(true);

            if (camelContext != null) {
                for (int i = 1; i < arguments.size(); i++) {
                    arguments.set(i, camelContext.getTypeConverter().convertTo(callbackMethod.getParameterTypes()[i], arguments.get(i)));
                }
            }

            return callbackMethod.invoke(objectWithCallback, arguments.toArray(new Object[arguments.size()]));
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}