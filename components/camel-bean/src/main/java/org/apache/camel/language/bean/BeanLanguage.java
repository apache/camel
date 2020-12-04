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

import java.net.URISyntaxException;
import java.util.Map;

import org.apache.camel.BeanScope;
import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StaticService;
import org.apache.camel.component.bean.BeanComponent;
import org.apache.camel.component.bean.ParameterMappingStrategy;
import org.apache.camel.component.bean.ParameterMappingStrategyHelper;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.component.PropertyConfigurerSupport;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

/**
 * A <a href="http://camel.apache.org/bean-language.html">bean language</a> which uses a simple text notation to invoke
 * methods on beans to evaluate predicates or expressions
 * <p/>
 * The notation is essentially <code>beanName.methodName</code> which is then invoked using the beanName to lookup in
 * the <a href="http://camel.apache.org/registry.html>registry</a> then the method is invoked to evaluate the expression
 * using the <a href="http://camel.apache.org/bean-integration.html">bean integration</a> to bind the
 * {@link org.apache.camel.Exchange} to the method arguments.
 * <p/>
 * As of Camel 1.5 the bean language also supports invoking a provided bean by its classname or the bean itself.
 */
@org.apache.camel.spi.annotations.Language("bean")
public class BeanLanguage extends LanguageSupport implements PropertyConfigurer, StaticService {

    private volatile BeanComponent beanComponent;
    private volatile ParameterMappingStrategy parameterMappingStrategy;
    private volatile Language simple;

    private Object bean;
    private Class<?> beanType;
    private String ref;
    private String method;
    private BeanScope scope = BeanScope.Singleton;

    public BeanLanguage() {
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

    public BeanScope getScope() {
        return scope;
    }

    public void setScope(BeanScope scope) {
        this.scope = scope;
    }

    @Override
    public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
        if (target != this) {
            throw new IllegalStateException("Can only configure our own instance !");
        }
        switch (ignoreCase ? name.toLowerCase() : name) {
            case "bean":
                setBean(PropertyConfigurerSupport.property(camelContext, Object.class, value));
                return true;
            case "beantype":
            case "beanType":
                setBeanType(PropertyConfigurerSupport.property(camelContext, Class.class, value));
                return true;
            case "ref":
                setRef(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "method":
                setMethod(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "scope":
                setScope(PropertyConfigurerSupport.property(camelContext, BeanScope.class, value));
                return true;
            default:
                return false;
        }
    }

    @Override
    public Predicate createPredicate(String expression) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression));
    }

    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression, properties));
    }

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        BeanExpression answer = null;

        String method = (String) properties[1];
        Object bean = properties[0];
        if (bean != null) {
            answer = new BeanExpression(bean, method);
        }
        if (answer == null) {
            Class<?> beanType = (Class<?>) properties[2];
            if (beanType != null) {
                answer = new BeanExpression(beanType, method);
            }
        }
        if (answer == null) {
            String ref = (String) properties[3];
            if (ref != null) {
                answer = new BeanExpression(ref, method);
            }
        }
        if (answer == null) {
            throw new IllegalArgumentException("Bean language requires bean, beanType, or ref argument");
        }
        if (properties.length == 5) {
            Object scope = properties[4];
            if (scope instanceof BeanScope) {
                answer.setScope((BeanScope) scope);
            } else if (scope != null) {
                answer.setScope(BeanScope.valueOf(scope.toString()));
            }
        }
        answer.setBeanComponent(beanComponent);
        answer.setParameterMappingStrategy(parameterMappingStrategy);
        answer.setSimple(simple);
        answer.init(getCamelContext());
        return answer;
    }

    @Override
    public Expression createExpression(String expression) {
        BeanExpression answer;
        String beanScope = null;

        // favour using the configured options
        if (bean != null) {
            answer = new BeanExpression(bean, method);
        } else if (beanType != null) {
            answer = new BeanExpression(beanType, method);
        } else if (ref != null) {
            answer = new BeanExpression(ref, method);
        } else {
            // we support different syntax for bean function
            String beanName = expression;
            String method = null;
            if (expression.contains("?method=") || expression.contains("?scope=")) {
                beanName = StringHelper.before(expression, "?");
                String query = StringHelper.after(expression, "?");
                try {
                    Map<String, Object> map = URISupport.parseQuery(query);
                    method = (String) map.get("method");
                    beanScope = (String) map.get("scope");
                } catch (URISyntaxException e) {
                    throw RuntimeCamelException.wrapRuntimeException(e);
                }
            } else {
                //first check case :: because of my.own.Bean::method
                int doubleColonIndex = expression.indexOf("::");
                //need to check that not inside params
                int beginOfParameterDeclaration = expression.indexOf('(');
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

            if (beanName.startsWith("type:")) {
                try {
                    Class clazz = getCamelContext().getClassResolver().resolveMandatoryClass(beanName.substring(5));
                    answer = new BeanExpression(clazz, method);
                } catch (ClassNotFoundException e) {
                    throw RuntimeCamelException.wrapRuntimeException(e);
                }
            } else {
                answer = new BeanExpression(beanName, method);
            }
        }

        if (beanScope != null) {
            answer.setScope(getCamelContext().getTypeConverter().tryConvertTo(BeanScope.class, beanScope));
        } else {
            answer.setScope(scope);
        }
        answer.setBeanComponent(beanComponent);
        answer.setParameterMappingStrategy(parameterMappingStrategy);
        answer.setSimple(simple);
        answer.init(getCamelContext());
        return answer;
    }

    @Override
    public void start() {
        beanComponent = getCamelContext().getComponent("bean", BeanComponent.class);
        parameterMappingStrategy = ParameterMappingStrategyHelper.createParameterMappingStrategy(getCamelContext());
        simple = getCamelContext().resolveLanguage("simple");
    }

    @Override
    public void stop() {
        // noop
    }
}
