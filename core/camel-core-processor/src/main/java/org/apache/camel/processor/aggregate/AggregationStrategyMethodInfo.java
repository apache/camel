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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.RuntimeCamelException;

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
                Object value = evaluate(info, oldExchange);
                list.add(value);
            } else {
                // use a null value as oldExchange is null
                list.add(null);
            }
        }
        for (AggregationStrategyParameterInfo info : newParameters) {
            if (newExchange != null) {
                Object value = evaluate(info, newExchange);
                list.add(value);
            } else {
                // use a null value as newExchange is null
                list.add(null);
            }
        }

        Object[] args = list.toArray();
        return method.invoke(pojo, args);
    }

    private Object evaluate(AggregationStrategyParameterInfo info, Exchange exchange) {
        try {
            return info.getExpression().evaluate(exchange, info.getType());
        } catch (CamelExecutionException e) {
            if (e.getCause() instanceof InvalidPayloadException && exchange.getMessage().getBody() == null) {
                // a typed parameter is bound to the message body, and there is none (a timer message is empty)
                throw new RuntimeCamelException(
                        "The aggregation strategy method " + method.getName() + " has a parameter of type "
                                                + info.getType().getSimpleName()
                                                + " that is bound to the message body, but the message has no body (null):"
                                                + " set a body before the aggregate (setBody), or declare the parameter as Exchange or Object",
                        e.getCause());
            }
            throw e;
        }
    }

}
