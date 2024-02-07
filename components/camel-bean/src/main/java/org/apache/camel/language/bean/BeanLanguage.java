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

import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.BeanScope;
import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StaticService;
import org.apache.camel.component.bean.AmbiguousMethodCallException;
import org.apache.camel.component.bean.BeanComponent;
import org.apache.camel.component.bean.BeanInfo;
import org.apache.camel.component.bean.MethodInfo;
import org.apache.camel.component.bean.MethodNotFoundException;
import org.apache.camel.component.bean.ParameterMappingStrategy;
import org.apache.camel.component.bean.ParameterMappingStrategyHelper;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.TypedLanguageSupport;
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
public class BeanLanguage extends TypedLanguageSupport implements ScriptingLanguage, PropertyConfigurer, StaticService {
    public static final String LANGUAGE = "bean";

    private volatile BeanComponent beanComponent;
    private volatile ParameterMappingStrategy parameterMappingStrategy;
    private volatile Language simple;

    private boolean validate = true;

    public BeanLanguage() {
    }

    public boolean isValidate() {
        return validate;
    }

    public void setValidate(boolean validate) {
        this.validate = validate;
    }

    @Override
    public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
        if (target != this) {
            throw new IllegalStateException("Can only configure our own instance !");
        }
        switch (ignoreCase ? name.toLowerCase() : name) {
            case "validate":
                setValidate(PropertyConfigurerSupport.property(camelContext, Boolean.class, value));
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
    public Expression createExpression(String expression) {
        return createExpression(expression, null);
    }

    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression, properties));
    }

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        BeanExpression answer = null;

        Object bean = property(Object.class, properties, 1, null);
        String method = property(String.class, properties, 2, null);
        if (bean != null) {
            answer = new BeanExpression(bean, method);
        }
        if (answer == null) {
            Class<?> beanType = property(Class.class, properties, 3, null);
            if (beanType != null) {
                answer = new BeanExpression(beanType, method);
            }
        }
        if (answer == null) {
            String ref = property(String.class, properties, 4, null);
            if (ref != null) {
                answer = new BeanExpression(ref, method);
            }
        }
        if (answer == null) {
            answer = createBeanExpression(expression);
        }
        if (answer == null) {
            throw new IllegalArgumentException("Bean language requires bean, beanType, or ref argument");
        }
        Object scope = property(Object.class, properties, 5, null);
        if (scope instanceof BeanScope) {
            answer.setScope((BeanScope) scope);
        } else if (scope != null) {
            answer.setScope(BeanScope.valueOf(scope.toString()));
        }
        answer.setValidate(property(boolean.class, properties, 6, isValidate()));
        answer.setResultType(property(Class.class, properties, 0, null));
        answer.setBeanComponent(beanComponent);
        answer.setParameterMappingStrategy(parameterMappingStrategy);
        answer.setSimple(simple);
        if (getCamelContext() != null) {
            answer.init(getCamelContext());
        }
        return answer;
    }

    protected BeanExpression createBeanExpression(String expression) {
        BeanExpression answer;

        // we support different syntax for bean function
        String beanName = expression;
        String method = null;
        String beanScope = null;
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
                Class<?> clazz = getCamelContext().getClassResolver().resolveMandatoryClass(beanName.substring(5));
                answer = new BeanExpression(clazz, method);
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        } else {
            answer = new BeanExpression(beanName, method);
        }
        if (beanScope != null) {
            answer.setScope(BeanScope.valueOf(beanScope));
        }
        return answer;
    }

    @Override
    public <T> T evaluate(String script, Map<String, Object> bindings, Class<T> resultType) {
        script = loadResource(script);
        String beanName = StringHelper.before(script, "?method=");
        String beanMethod = StringHelper.after(script, "?method=");

        try {
            Class<?> clazz = getCamelContext().getClassResolver().resolveMandatoryClass(beanName);
            // find methods with that name
            BeanInfo bi = new BeanInfo(getCamelContext(), clazz);

            // find method that is the best candidate
            // match by number of arguments
            List<MethodInfo> candidates = new ArrayList<>();
            for (MethodInfo mi : bi.getMethods()) {
                if (mi.getMethod().getName().equals(beanMethod)) {
                    // must match number of args
                    int size = bindings != null ? bindings.size() : 0;
                    boolean match = mi.getParameters().size() == size;
                    if (match) {
                        candidates.add(mi);
                    }
                }
            }
            // if there are a method with no arguments, then we can use that as fallback
            if (candidates.isEmpty()) {
                MethodInfo fallback = null;
                for (MethodInfo mi : bi.getMethods()) {
                    if (mi.getMethod().getName().equals(beanMethod)) {
                        boolean match = !mi.hasParameters();
                        if (match) {
                            if (fallback == null) {
                                fallback = mi;
                            } else {
                                fallback = null;
                                break;
                            }
                        }
                    }
                }
                if (fallback != null) {
                    candidates.add(fallback);
                }
            }

            if (candidates.isEmpty()) {
                throw new MethodNotFoundException(clazz, beanMethod);
            } else if (candidates.size() > 1) {
                throw new AmbiguousMethodCallException(null, candidates);
            }

            Object out;
            MethodInfo mi = candidates.get(0);
            Method method = mi.getMethod();
            // map bindings to method
            Object[] args
                    = method.getParameterCount() > 0 && bindings != null ? bindings.values().toArray(new Object[0]) : null;
            if (mi.isStaticMethod()) {
                out = ObjectHelper.invokeMethod(method, null, args);
            } else {
                Object bean = getCamelContext().getInjector().newInstance(clazz);
                out = ObjectHelper.invokeMethod(method, bean, args);
            }
            if (out != null && resultType != null) {
                out = getCamelContext().getTypeConverter().convertTo(resultType, out);
            }
            return (T) out;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
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
