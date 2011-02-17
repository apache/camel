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
package org.apache.camel.component.bean;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.language.LanguageAnnotation;
import org.apache.camel.language.bean.BeanExpression;
import org.apache.camel.util.ObjectHelper;

/**
 * @version 
 */
public class BeanAnnotationExpressionFactory extends DefaultAnnotationExpressionFactory {

    @Override
    public Expression createExpression(CamelContext camelContext, Annotation annotation, LanguageAnnotation languageAnnotation, Class<?> expressionReturnType) {
        String beanName = getFromAnnotation(annotation, "ref");
        String method = getFromAnnotation(annotation, "method");

        // ref is mandatory
        ObjectHelper.notEmpty(beanName, "ref", annotation);

        // method is optional but provide it as null to the bean expression
        if (ObjectHelper.isEmpty(method)) {
            method = null;
        }

        return new BeanExpression(beanName, method);
    }

    protected String getFromAnnotation(Annotation annotation, String attribute) {
        try {
            Method method = annotation.getClass().getMethod(attribute);
            Object value = ObjectHelper.invokeMethod(method, annotation);
            if (value == null) {
                throw new IllegalArgumentException("Cannot determine the " + attribute + " from the annotation: " + annotation);
            }
            return value.toString();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Cannot determine the " + attribute
                + " of the annotation: " + annotation + " as it does not have a " + attribute + "() method");
        }
    }
}

