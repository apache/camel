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

import java.util.List;
import java.util.Map;

import org.apache.camel.BeanScope;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.bean.BeanComponent;
import org.apache.camel.component.bean.BeanExpressionProcessor;
import org.apache.camel.component.bean.BeanHolder;
import org.apache.camel.component.bean.BeanInfo;
import org.apache.camel.component.bean.ConstantBeanHolder;
import org.apache.camel.component.bean.ConstantTypeBeanHolder;
import org.apache.camel.component.bean.MethodNotFoundException;
import org.apache.camel.component.bean.ParameterMappingStrategy;
import org.apache.camel.component.bean.ParameterMappingStrategyHelper;
import org.apache.camel.component.bean.RegistryBean;
import org.apache.camel.component.bean.RequestBeanHolder;
import org.apache.camel.spi.Language;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.util.ObjectHelper.hasDefaultPublicNoArgConstructor;

/**
 * Evaluates an expression using a bean method invocation
 */
public class BeanExpression implements Expression, Predicate {

    private ParameterMappingStrategy parameterMappingStrategy;
    private BeanComponent beanComponent;
    private Language simple;
    private Class<?> resultType;
    private Object bean;
    private String beanName;
    private Class<?> type;
    private String method;
    private BeanHolder beanHolder;
    private boolean ognlMethod;
    private BeanScope scope = BeanScope.Singleton;
    private boolean validate = true;

    public BeanExpression(Object bean, String method) {
        this.bean = bean;
        this.method = method;
        this.beanName = null;
        this.type = null;
    }

    public BeanExpression(String beanName, String method) {
        this.beanName = beanName;
        this.method = method;
        this.bean = null;
        this.type = null;
    }

    public BeanExpression(Class<?> type, String method) {
        this.type = type;
        this.method = method;
        this.bean = null;
        this.beanName = null;
    }

    public Object getBean() {
        return bean;
    }

    public String getBeanName() {
        return beanName;
    }

    public Class<?> getType() {
        return type;
    }

    public String getMethod() {
        return method;
    }

    public BeanScope getScope() {
        return scope;
    }

    public void setScope(BeanScope scope) {
        this.scope = scope;
    }

    public boolean isValidate() {
        return validate;
    }

    public void setValidate(boolean validate) {
        this.validate = validate;
    }

    public ParameterMappingStrategy getParameterMappingStrategy() {
        return parameterMappingStrategy;
    }

    public void setParameterMappingStrategy(ParameterMappingStrategy parameterMappingStrategy) {
        this.parameterMappingStrategy = parameterMappingStrategy;
    }

    public BeanComponent getBeanComponent() {
        return beanComponent;
    }

    public void setBeanComponent(BeanComponent beanComponent) {
        this.beanComponent = beanComponent;
    }

    public Language getSimple() {
        return simple;
    }

    public void setSimple(Language simple) {
        this.simple = simple;
    }

    public Class<?> getResultType() {
        return resultType;
    }

    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    @Override
    public void init(CamelContext context) {
        if (parameterMappingStrategy == null) {
            parameterMappingStrategy = ParameterMappingStrategyHelper.createParameterMappingStrategy(context);
        }
        if (beanComponent == null) {
            beanComponent = context.getComponent("bean", BeanComponent.class);
        }
        if (beanName != null && beanName.startsWith("type:")) {
            // its a reference to a fqn class so load the class and use type instead
            String fqn = beanName.substring(5);
            try {
                type = context.getClassResolver().resolveMandatoryClass(fqn);
                beanName = null;
            } catch (ClassNotFoundException e) {
                throw new NoSuchBeanException(beanName, e);
            }
        }
        if (beanHolder == null) {
            beanHolder = createBeanHolder(context, parameterMappingStrategy, beanComponent);
        }

        // lets see if we can do additional validation that the bean has valid method during creation of the expression
        Object target = beanHolder.getBean(null);
        if (method != null) {
            if (validate) {
                validateHasMethod(context, target, type, method);
                // validate OGNL if its invalid syntax
                if (OgnlHelper.isInvalidValidOgnlExpression(method)) {
                    throw new ExpressionIllegalSyntaxException(method);
                }
            }
            ognlMethod = OgnlHelper.isValidOgnlExpression(method);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("BeanExpression[");
        if (bean != null) {
            sb.append(bean.toString());
        } else if (beanName != null) {
            sb.append(beanName);
        } else if (type != null) {
            sb.append(ObjectHelper.className(type));
        }
        if (method != null) {
            sb.append(" method:").append(method);
        }
        sb.append("]");
        return sb.toString();
    }

    public Object evaluate(Exchange exchange) {
        if (beanHolder == null) {
            throw new IllegalStateException("The expression must be initialized first");
        }
        try {
            if (ognlMethod) {
                // okay the method is an ognl expression
                return invokeOgnlMethod(beanHolder, beanName, method, exchange);
            } else {
                // regular non ognl invocation
                return invokeBean(beanHolder, beanName, method, exchange);
            }
        } catch (RuntimeBeanExpressionException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeBeanExpressionException(exchange, getBeanName(exchange, beanName, beanHolder), method, e);
        }
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        Object result = evaluate(exchange);
        if (Object.class == type) {
            // do not use type converter if type is Object (optimize)
            return (T) (resultType == null
                    ? result : exchange.getContext().getTypeConverter().convertTo(resultType, exchange, result));
        } else {
            return exchange.getContext().getTypeConverter().convertTo(type, exchange, result);
        }
    }

    @Override
    public boolean matches(Exchange exchange) {
        Object value = evaluate(exchange);
        return ObjectHelper.evaluateValuePredicate(value);
    }

    /**
     * Validates the given bean has the method.
     * <p/>
     * This implementation will skip trying to validate OGNL method name expressions.
     *
     * @param  context                                camel context
     * @param  bean                                   the bean instance
     * @param  type                                   the bean type
     * @param  method                                 the method, can be <tt>null</tt> if no method name provided
     * @throws org.apache.camel.RuntimeCamelException is thrown if bean does not have the method
     */
    protected void validateHasMethod(CamelContext context, Object bean, Class<?> type, String method) {
        if (method == null) {
            return;
        }

        if (bean == null && type == null) {
            throw new IllegalArgumentException("Either bean or type should be provided on " + this);
        }

        // do not try to validate ognl methods
        if (OgnlHelper.isValidOgnlExpression(method)) {
            return;
        }

        if (bean == null && hasDefaultPublicNoArgConstructor(type)) {
            bean = context.getInjector().newInstance(type);
        }

        // if invalid OGNL then fail
        if (OgnlHelper.isInvalidValidOgnlExpression(method)) {
            ExpressionIllegalSyntaxException cause = new ExpressionIllegalSyntaxException(method);
            throw RuntimeCamelException
                    .wrapRuntimeCamelException(new MethodNotFoundException(bean != null ? bean : type, method, cause));
        }

        if (bean != null) {
            BeanInfo info = new BeanInfo(context, bean.getClass(), parameterMappingStrategy, beanComponent);
            if (!info.hasMethod(method)) {
                throw RuntimeCamelException.wrapRuntimeCamelException(new MethodNotFoundException(null, bean, method));
            }
        } else {
            BeanInfo info = new BeanInfo(context, type, parameterMappingStrategy, beanComponent);
            // must be a static method as we do not have a bean instance to invoke
            if (!info.hasStaticMethod(method)) {
                throw RuntimeCamelException.wrapRuntimeCamelException(new MethodNotFoundException(null, type, method, true));
            }
        }
    }

    private BeanHolder createBeanHolder(
            CamelContext context, ParameterMappingStrategy parameterMappingStrategy, BeanComponent beanComponent) {
        // either use registry lookup or a constant bean
        BeanHolder holder = null;
        if (bean != null) {
            holder = new ConstantBeanHolder(bean, context, parameterMappingStrategy, beanComponent);
        } else if (beanName != null) {
            RegistryBean rb = new RegistryBean(context, beanName, parameterMappingStrategy, beanComponent);
            if (scope == BeanScope.Singleton) {
                // cache holder as its singleton
                holder = rb.createCacheHolder();
            } else if (scope == BeanScope.Request) {
                // wrap in registry scoped
                holder = new RequestBeanHolder(rb);
            } else {
                // prototype scope will lookup bean on each access
                holder = rb;
            }
        } else if (type != null) {
            ConstantTypeBeanHolder th = new ConstantTypeBeanHolder(type, context, parameterMappingStrategy, beanComponent);
            if (scope == BeanScope.Singleton && ObjectHelper.hasDefaultPublicNoArgConstructor(type)) {
                // we can only cache if we can create an instance of the bean, and for that we need a public constructor
                holder = th.createCacheHolder();
            } else if (scope == BeanScope.Request) {
                // wrap in registry scoped
                holder = new RequestBeanHolder(th);
            } else {
                holder = th;
            }
        } else {
            throw new IllegalArgumentException("Either bean, beanName or type should be set on " + this);
        }
        return holder;
    }

    private static String getBeanName(Exchange exchange, String beanName, BeanHolder beanHolder) {
        String name = beanName;
        if (name == null && beanHolder != null && beanHolder.getBean(exchange) != null) {
            name = beanHolder.getBean(exchange).getClass().getCanonicalName();
        }
        if (name == null && beanHolder != null && beanHolder.getBeanInfo() != null
                && beanHolder.getBeanInfo().getType() != null) {
            name = beanHolder.getBeanInfo().getType().getCanonicalName();
        }
        return name;
    }

    /**
     * Invokes the bean and returns the result. If an exception was thrown while invoking the bean, then the exception
     * is set on the exchange.
     */
    private static Object invokeBean(BeanHolder beanHolder, String beanName, String methodName, Exchange exchange) {
        Object result;

        BeanExpressionProcessor processor = new BeanExpressionProcessor(beanHolder);
        if (methodName != null) {
            processor.setMethod(methodName);
            // enable OGNL like invocation
            processor.setShorthandMethod(true);
        }
        try {
            // copy the original exchange to avoid side effects on it
            Exchange resultExchange = ExchangeHelper.createCopy(exchange, true);
            // remove any existing exception in case we do OGNL on the exception
            resultExchange.setException(null);

            // force to use InOut to retrieve the result on the OUT message
            resultExchange.setPattern(ExchangePattern.InOut);
            processor.process(resultExchange);
            // the response is always stored in OUT
            result = resultExchange.hasOut() ? resultExchange.getOut().getBody() : null;

            // propagate properties and headers from result
            if (resultExchange.hasProperties()) {
                exchange.getProperties().putAll(resultExchange.getProperties());
            }
            if (resultExchange.hasOut() && resultExchange.getOut().hasHeaders()) {
                exchange.getIn().getHeaders().putAll(resultExchange.getOut().getHeaders());
            }

            // propagate exceptions
            if (resultExchange.getException() != null) {
                exchange.setException(resultExchange.getException());
            }
        } catch (Exception e) {
            throw new RuntimeBeanExpressionException(exchange, beanName, methodName, e);
        }

        return result;
    }

    /**
     * To invoke a bean using a OGNL notation which denotes the chain of methods to invoke.
     * <p/>
     * For more advanced OGNL you may have to look for a real framework such as OGNL, Mvel or dynamic programming
     * language such as Groovy.
     */
    private Object invokeOgnlMethod(BeanHolder beanHolder, String beanName, String ognl, Exchange exchange) {

        // we must start with having bean as the result
        Object result = beanHolder.getBean(exchange);

        // copy the original exchange to avoid side effects on it
        Exchange resultExchange = ExchangeHelper.createCopy(exchange, true);
        // remove any existing exception in case we do OGNL on the exception
        resultExchange.setException(null);
        // force to use InOut to retrieve the result on the OUT message
        resultExchange.setPattern(ExchangePattern.InOut);
        // do not propagate any method name when using OGNL, as with OGNL we
        // compute and provide the method name to explicit to invoke
        resultExchange.getIn().removeHeader(Exchange.BEAN_METHOD_NAME);

        // current ognl path as we go along
        String ognlPath = "";

        // loop and invoke each method
        Object beanToCall = beanHolder.getBean(exchange);
        Class<?> beanType = beanHolder.getBeanInfo().getType();

        // there must be a bean to call with, we currently does not support OGNL expressions on using purely static methods
        if (beanToCall == null && beanType == null) {
            throw new IllegalArgumentException(
                    "Bean instance and bean type is null. OGNL bean expressions requires to have either a bean instance of the class name of the bean to use.");
        }

        if (ognl != null) {
            // must be a valid method name according to java identifier ruling
            OgnlHelper.validateMethodName(ognl);
        }

        // Split ognl except when this is not a Map, Array
        // and we would like to keep the dots within the key name
        List<String> methods = OgnlHelper.splitOgnl(ognl);

        for (String methodName : methods) {
            BeanHolder holder;
            if (beanToCall != null) {
                holder = new ConstantBeanHolder(beanToCall, exchange.getContext(), parameterMappingStrategy, beanComponent);
            } else if (beanType != null) {
                holder = new ConstantTypeBeanHolder(beanType, exchange.getContext(), parameterMappingStrategy, beanComponent);
            } else {
                holder = null;
            }

            // support the null safe operator
            boolean nullSafe = OgnlHelper.isNullSafeOperator(methodName);

            if (holder == null) {
                String name = getBeanName(exchange, null, beanHolder);
                throw new RuntimeBeanExpressionException(
                        exchange, name, ognl, "last method returned null and therefore cannot continue to invoke method "
                                              + methodName + " on a null instance");
            }

            // keep up with how far are we doing
            ognlPath += methodName;

            // get rid of leading ?. or . as we only needed that to determine if null safe was enabled or not
            methodName = OgnlHelper.removeLeadingOperators(methodName);

            // are we doing an index lookup (eg in Map/List/array etc)?
            String key = null;
            KeyValueHolder<String, String> index = OgnlHelper.isOgnlIndex(methodName);
            if (index != null) {
                methodName = index.getKey();
                key = index.getValue();
            }

            // only invoke if we have a method name to use to invoke
            if (methodName != null) {
                Object newResult = invokeBean(holder, beanName, methodName, resultExchange);

                // check for exception and rethrow if we failed
                if (resultExchange.getException() != null) {
                    throw new RuntimeBeanExpressionException(exchange, beanName, methodName, resultExchange.getException());
                }

                result = newResult;
            }

            // if there was a key then we need to lookup using the key
            if (key != null) {
                // if key is a nested simple expression then re-evaluate that again
                if (LanguageSupport.hasSimpleFunction(key)) {
                    Expression exp = simple.createExpression(key);
                    exp.init(exchange.getContext());
                    key = exp.evaluate(exchange, String.class);
                }
                if (key != null) {
                    result = lookupResult(resultExchange, key, result, nullSafe, ognlPath, holder.getBean(exchange));
                }
            }

            // check null safe for null results
            if (result == null && nullSafe) {
                return null;
            }

            // prepare for next bean to invoke
            beanToCall = result;
            beanType = null;
        }

        return result;
    }

    private static Object lookupResult(
            Exchange exchange, String key, Object result, boolean nullSafe, String ognlPath, Object bean) {
        StringHelper.notEmpty(key, "key", "in Simple language ognl path: " + ognlPath);

        final TypeConverter typeConverter = exchange.getContext().getTypeConverter();

        // trim key
        key = key.trim();

        // remove any enclosing quotes
        key = StringHelper.removeLeadingAndEndingQuotes(key);

        // try map first
        Map<?, ?> map = typeConverter.convertTo(Map.class, result);
        if (map != null) {
            return map.get(key);
        }

        // special for list is last keyword
        Integer num = typeConverter.tryConvertTo(Integer.class, key);
        boolean checkList = key.startsWith("last") || num != null;

        if (checkList) {
            List<?> list = typeConverter.convertTo(List.class, result);
            if (list != null) {
                if (key.startsWith("last")) {
                    num = list.size() - 1;

                    // maybe its an expression to subtract a number after last
                    String after = StringHelper.after(key, "-");
                    if (after != null) {
                        Integer redux = typeConverter.tryConvertTo(Integer.class, after.trim());
                        if (redux != null) {
                            num -= redux;
                        } else {
                            throw new ExpressionIllegalSyntaxException(key);
                        }
                    }
                }
                if (num != null && num >= 0 && !list.isEmpty() && list.size() > num - 1) {
                    return list.get(num);
                }
                if (!nullSafe) {
                    // not null safe then its mandatory so thrown out of bounds exception
                    throw new IndexOutOfBoundsException(
                            "Index: " + num + ", Size: " + list.size()
                                                        + " out of bounds with List from bean: " + bean + "using OGNL path ["
                                                        + ognlPath + "]");
                }
            }
        }

        if (!nullSafe) {
            throw new IndexOutOfBoundsException(
                    "Key: " + key + " not found in bean: " + bean + " of type: "
                                                + ObjectHelper.classCanonicalName(bean) + " using OGNL path [" + ognlPath
                                                + "]");
        } else {
            // null safe so we can return null
            return null;
        }
    }

}
