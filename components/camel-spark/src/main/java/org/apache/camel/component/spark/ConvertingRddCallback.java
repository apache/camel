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
package org.apache.camel.component.spark;

import org.apache.camel.CamelContext;
import org.apache.spark.api.java.JavaRDDLike;

import static java.lang.String.format;

public abstract class ConvertingRddCallback<T> implements RddCallback<T> {

    private final CamelContext camelContext;

    private final Class[] payloadsTypes;

    public ConvertingRddCallback(CamelContext camelContext, Class... payloadsTypes) {
        this.camelContext = camelContext;
        this.payloadsTypes = payloadsTypes;
    }

    @Override
    public T onRdd(JavaRDDLike rdd, Object... payloads) {
        if (payloads.length != payloadsTypes.length) {
            String message = format("Received %d payloads, but expected %d.", payloads.length, payloadsTypes.length);
            throw new IllegalArgumentException(message);
        }
        for (int i = 0; i < payloads.length; i++) {
            payloads[i] = camelContext.getTypeConverter().convertTo(payloadsTypes[i], payloads[i]);
        }
        return doOnRdd(rdd, payloads);
    }

    public abstract T doOnRdd(JavaRDDLike rdd, Object... payloads);

}
