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
package org.apache.camel.language.bean;

import org.apache.camel.Expression;
import org.apache.camel.IsSingleton;
import org.apache.camel.Predicate;
import org.apache.camel.spi.Language;
import org.apache.camel.util.ExpressionToPredicateAdapter;
import org.apache.camel.util.ObjectHelper;

/**
 * A <a href="http://camel.apache.org/bean-language.html">bean language</a>
 * which uses a simple text notation to invoke methods on beans to evaluate predicates or expressions
 * <p/>
 * The notation is essentially <code>beanName.methodName</code> which is then invoked using the
 * beanName to lookup in the <a href="http://camel.apache.org/registry.html>registry</a>
 * then the method is invoked to evaluate the expression using the
 * <a href="http://camel.apache.org/bean-integration.html">bean integration</a> to bind the
 * {@link org.apache.camel.Exchange} to the method arguments.
 * <p/>
 * As of Camel 1.5 the bean language also supports invoking a provided bean by
 * its classname or the bean itself.
 *
 * @version 
 */
public class BeanLanguage implements Language, IsSingleton {

    /**
     * Creates the expression based on the string syntax.
     *
     * @param expression the string syntax <tt>beanRef.methodName</tt> where methodName can be omitted
     * @return the expression
     */
    public static Expression bean(String expression) {
        BeanLanguage language = new BeanLanguage();
        return language.createExpression(expression);
    }

    /**
     * Creates the expression for invoking the bean type.
     *
     * @param beanType  the bean type to invoke
     * @param method optional name of method to invoke for instance to avoid ambiguity
     * @return the expression
     */
    public static Expression bean(Class<?> beanType, String method) {
        Object bean = ObjectHelper.newInstance(beanType);
        return bean(bean, method);
    }

    /**
     * Creates the expression for invoking the bean type.
     *
     * @param bean  the bean to invoke
     * @param method optional name of method to invoke for instance to avoid ambiguity
     * @return the expression
     */
    public static Expression bean(Object bean, String method) {
        BeanLanguage language = new BeanLanguage();
        return language.createExpression(bean, method);
    }

    public Predicate createPredicate(String expression) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression));
    }

    public Expression createExpression(String expression) {
        ObjectHelper.notNull(expression, "expression");

        String beanName = expression;
        String method = null;

        // we support both the .method name and the ?method= syntax
        // as the ?method= syntax is very common for the bean component
        if (expression.contains("?method=")) {
            beanName = ObjectHelper.before(expression, "?");
            method = ObjectHelper.after(expression, "?method=");
        } else {
            int idx = expression.indexOf('.');
            if (idx > 0) {
                beanName = expression.substring(0, idx);
                method = expression.substring(idx + 1);
            }
        }

        return new BeanExpression(beanName, method);
    }

    public Expression createExpression(Object bean, String method) {
        ObjectHelper.notNull(bean, "bean");
        return new BeanExpression(bean, method);
    }

    public boolean isSingleton() {
        return true;
    }
}