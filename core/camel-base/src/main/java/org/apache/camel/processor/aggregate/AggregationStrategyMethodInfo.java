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
package org.apache.camel.processor.aggregate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;

/**
 * Method information about the POJO method to call when using the {@link AggregationStrategyBeanAdapter}.
 */
public class AggregationStrategyMethodInfo {

    private final Method method;
    private final List<AggregationStrategyParameterInfo> oldParameters;
    private final List<AggregationStrategyParameterInfo> newParameters;


    public AggregationStrategyMethodInfo(Method method,
                                         List<AggregationStrategyParameterInfo> oldParameters,
                                         List<AggregationStrategyParameterInfo> newParameters) {
        this.method = method;
        this.oldParameters = oldParameters;
        this.newParameters = newParameters;
    }

    public Object invoke(Object pojo, Exchange oldExchange, Exchange newExchange) throws Exception {
        // evaluate the parameters
        List<Object> list = new ArrayList<>(oldParameters.size() + newParameters.size());
        for (AggregationStrategyParameterInfo info : oldParameters) {
            if (oldExchange != null) {
                Object value = info.getExpression().evaluate(oldExchange, info.getType());
                list.add(value);
            } else {
                // use a null value as oldExchange is null
                list.add(null);
            }
        }
        for (AggregationStrategyParameterInfo info : newParameters) {
            if (newExchange != null) {
                Object value = info.getExpression().evaluate(newExchange, info.getType());
                list.add(value);
            } else {
                // use a null value as newExchange is null
                list.add(null);
            }
        }

        Object[] args = list.toArray();
        return method.invoke(pojo, args);
    }

}
