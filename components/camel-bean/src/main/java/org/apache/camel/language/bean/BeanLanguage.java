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
package org.apache.camel.language.bean;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.component.PropertyConfigurerSupport;
import org.apache.camel.util.StringHelper;

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
 */
@org.apache.camel.spi.annotations.Language("bean")
public class BeanLanguage extends LanguageSupport implements GeneratedPropertyConfigurer {

    private Object bean;
    private Class<?> beanType;
    private String ref;
    private String method;

    public BeanLanguage() {
    }

    @Override
    public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
        if (target != this) {
            throw new IllegalStateException("Can only configure our own instance !");
        }
        switch (ignoreCase ? name.toLowerCase() : name) {
            case "bean":
                setBean(PropertyConfigurerSupport.property(camelContext, Object.class, value)); return true;
            case "beantype":
            case "beanType":
                setBeanType(PropertyConfigurerSupport.property(camelContext, Class.class, value)); return true;
            case "ref":
                setRef(PropertyConfigurerSupport.property(camelContext, String.class, value)); return true;
            case "method":
                setMethod(PropertyConfigurerSupport.property(camelContext, String.class, value)); return true;
            default:
                return false;
        }
    }

    public Object getBean() {
        return bean;
    }

    public void setBean(Object bean) {
        this.bean = bean;
    }

    public Class<?> getBeanType() {
        return beanType;
    }

    public void setBeanType(Class<?> beanType) {
        this.beanType = beanType;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    @Override
    public Predicate createPredicate(String expression) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression));
    }

    @Override
    public Expression createExpression(String expression) {
        // favour using the configured options
        if (bean != null) {
            return new BeanExpression(bean, method);
        } else if (beanType != null) {
            return new BeanExpression(beanType, method);
        } else if (ref != null) {
            return new BeanExpression(ref, method);
        }

        String beanName = expression;
        String method = null;

        // we support both the .method name and the ?method= syntax
        // as the ?method= syntax is very common for the bean component
        if (expression.contains("?method=")) {
            beanName = StringHelper.before(expression, "?");
            method = StringHelper.after(expression, "?method=");
        } else {
            //first check case :: because of my.own.Bean::method
            int doubleColonIndex = expression.indexOf("::");
            //need to check that not inside params
            int beginOfParameterDeclaration = expression.indexOf("(");
            if (doubleColonIndex > 0 && (!expression.contains("(") || doubleColonIndex < beginOfParameterDeclaration)) {
                beanName = expression.substring(0, doubleColonIndex);
                method = expression.substring(doubleColonIndex + 2);
            } else {
                int idx = expression.indexOf('.');
                if (idx > 0) {
                    beanName = expression.substring(0, idx);
                    method = expression.substring(idx + 1);
                }
            }
        }

        return new BeanExpression(beanName, method);
    }

    @Override
    public boolean isSingleton() {
        return false;
    }
}
