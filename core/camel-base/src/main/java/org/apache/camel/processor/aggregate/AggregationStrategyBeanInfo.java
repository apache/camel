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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Expression;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class information about the POJO method to call when using the {@link AggregationStrategyBeanAdapter}.
 */
public class AggregationStrategyBeanInfo {

    private static final Logger LOG = LoggerFactory.getLogger(AggregationStrategyBeanInfo.class);

    private final Class<?> type;
    private final Method method;

    public AggregationStrategyBeanInfo(Class<?> type, Method method) {
        this.type = type;
        this.method = method;
    }

    protected AggregationStrategyMethodInfo createMethodInfo() {
        Class<?>[] parameterTypes = method.getParameterTypes();

        int size = parameterTypes.length;
        if (LOG.isTraceEnabled()) {
            LOG.trace("Creating MethodInfo for class: {} method: {} having {} parameters", type, method, size);
        }

        // must have equal number of parameters
        if (size < 2) {
            throw new IllegalArgumentException("The method " + method.getName() + " must have at least two parameters, has: " + size);
        } else if (size % 2 != 0) {
            throw new IllegalArgumentException("The method " + method.getName() + " must have equal number of parameters, has: " + size);
        }

        // must not have annotations as they are not supported (yet)
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        for (int i = 0; i < parameterAnnotations.length; i++) {
            Annotation[] annotations = parameterAnnotations[i];
            if (annotations.length > 0) {
                throw new IllegalArgumentException("Method parameter annotation: " + annotations[0] + " at index: " + i + " is not supported on method: " + method);
            }
        }

        List<AggregationStrategyParameterInfo> oldParameters = new ArrayList<>();
        List<AggregationStrategyParameterInfo> newParameters = new ArrayList<>();

        for (int i = 0; i < size / 2; i++) {
            Class<?> oldType = parameterTypes[i];
            if (oldParameters.size() == 0) {
                // the first parameter is the body
                Expression oldBody = ExpressionBuilder.mandatoryBodyExpression(oldType);
                AggregationStrategyParameterInfo info = new AggregationStrategyParameterInfo(i, oldType, oldBody);
                oldParameters.add(info);
            } else if (oldParameters.size() == 1) {
                // the 2nd parameter is the headers
                Expression oldHeaders = ExpressionBuilder.headersExpression();
                AggregationStrategyParameterInfo info = new AggregationStrategyParameterInfo(i, oldType, oldHeaders);
                oldParameters.add(info);
            } else if (oldParameters.size() == 2) {
                // the 3rd parameter is the properties
                Expression oldProperties = ExpressionBuilder.exchangePropertiesExpression();
                AggregationStrategyParameterInfo info = new AggregationStrategyParameterInfo(i, oldType, oldProperties);
                oldParameters.add(info);
            }
        }

        for (int i = size / 2; i < size; i++) {
            Class<?> newType = parameterTypes[i];
            if (newParameters.size() == 0) {
                // the first parameter is the body
                Expression newBody = ExpressionBuilder.mandatoryBodyExpression(newType);
                AggregationStrategyParameterInfo info = new AggregationStrategyParameterInfo(i, newType, newBody);
                newParameters.add(info);
            } else if (newParameters.size() == 1) {
                // the 2nd parameter is the headers
                Expression newHeaders = ExpressionBuilder.headersExpression();
                AggregationStrategyParameterInfo info = new AggregationStrategyParameterInfo(i, newType, newHeaders);
                newParameters.add(info);
            } else if (newParameters.size() == 2) {
                // the 3rd parameter is the properties
                Expression newProperties = ExpressionBuilder.exchangePropertiesExpression();
                AggregationStrategyParameterInfo info = new AggregationStrategyParameterInfo(i, newType, newProperties);
                newParameters.add(info);
            }
        }

        return new AggregationStrategyMethodInfo(method, oldParameters, newParameters);
    }

}
