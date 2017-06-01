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

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.Predicate;
import org.apache.camel.component.bean.BeanExpressionProcessor;
import org.apache.camel.component.bean.BeanHolder;
import org.apache.camel.component.bean.ConstantBeanHolder;
import org.apache.camel.component.bean.ConstantTypeBeanHolder;
import org.apache.camel.component.bean.RegistryBean;
import org.apache.camel.language.simple.SimpleLanguage;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;
import org.apache.camel.util.StringHelper;

/**
 * Evaluates an expression using a bean method invocation
 */
public class BeanExpression implements Expression, Predicate {
    private final Object bean;
    private final String beanName;
    private final Class<?> type;
    private final String method;
    private volatile BeanHolder beanHolder;

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

    public BeanExpression(BeanHolder beanHolder, String method) {
        this.beanHolder = beanHolder;
        this.method = method;
        this.bean = null;
        this.beanName = null;
        this.type = null;
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

        // if the bean holder doesn't exist then create it using the context from the exchange
        if (beanHolder == null) {
            beanHolder = createBeanHolder(exchange.getContext());
        }

        // invoking the bean can either be the easy way or using OGNL

        // validate OGNL
        if (OgnlHelper.isInvalidValidOgnlExpression(method)) {
            ExpressionIllegalSyntaxException cause = new ExpressionIllegalSyntaxException(method);
            throw new RuntimeBeanExpressionException(exchange, beanName, method, cause);
        }

        if (OgnlHelper.isValidOgnlExpression(method)) {
            // okay the method is an ognl expression
            try {
                return invokeOgnlMethod(beanHolder, beanName, method, exchange);
            } catch (Exception e) {
                if (e instanceof RuntimeBeanExpressionException) {
                    throw (RuntimeBeanExpressionException) e;
                }
                throw new RuntimeBeanExpressionException(exchange, getBeanName(beanName, beanHolder), method, e);
            }
        } else {
            // regular non ognl invocation
            try {
                return invokeBean(beanHolder, beanName, method, exchange);
            } catch (Exception e) {
                if (e instanceof RuntimeBeanExpressionException) {
                    throw (RuntimeBeanExpressionException) e;
                }
                throw new RuntimeBeanExpressionException(exchange, getBeanName(beanName, beanHolder), method, e);
            }
        }
    }

    public <T> T evaluate(Exchange exchange, Class<T> type) {
        Object result = evaluate(exchange);
        if (Object.class == type) {
            // do not use type converter if type is Object (optimize)
            return (T) result;
        } else {
            return exchange.getContext().getTypeConverter().convertTo(type, exchange, result);
        }
    }

    public boolean matches(Exchange exchange) {
        Object value = evaluate(exchange);
        return ObjectHelper.evaluateValuePredicate(value);
    }

    /**
     * Optimize to create the bean holder once, so we can reuse it for further
     * evaluation, which is faster.
     */
    private synchronized BeanHolder createBeanHolder(CamelContext context) {
        // either use registry lookup or a constant bean
        BeanHolder holder;
        if (bean != null) {
            holder = new ConstantBeanHolder(bean, context);
        } else if (beanName != null) {
            holder = new RegistryBean(context, beanName);
        } else if (type != null) {
            holder = new ConstantTypeBeanHolder(type, context);
        } else {
            throw new IllegalArgumentException("Either bean, beanName or type should be set on " + this);
        }
        return holder;
    }

    private static String getBeanName(String beanName, BeanHolder beanHolder) {
        String name = beanName;
        if (name == null && beanHolder != null && beanHolder.getBean() != null) {
            name = beanHolder.getBean().getClass().getCanonicalName();
        }
        if (name == null && beanHolder != null && beanHolder.getBeanInfo() != null && beanHolder.getBeanInfo().getType() != null) {
            name = beanHolder.getBeanInfo().getType().getCanonicalName();
        }
        return name;
    }

    /**
     * Invokes the bean and returns the result. If an exception was thrown while invoking the bean, then the
     * exception is set on the exchange.
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
            result = resultExchange.getOut().getBody();

            // propagate properties and headers from result
            if (resultExchange.hasProperties()) {
                exchange.getProperties().putAll(resultExchange.getProperties());
            }
            if (resultExchange.getOut().hasHeaders()) {
                exchange.getIn().getHeaders().putAll(resultExchange.getOut().getHeaders());
            }

            // propagate exceptions
            if (resultExchange.getException() != null) {
                exchange.setException(resultExchange.getException());
            }
        } catch (Throwable e) {
            throw new RuntimeBeanExpressionException(exchange, beanName, methodName, e);
        }

        return result;
    }

    /**
     * To invoke a bean using a OGNL notation which denotes the chain of methods to invoke.
     * <p/>
     * For more advanced OGNL you may have to look for a real framework such as OGNL, Mvel or dynamic
     * programming language such as Groovy, JuEL, JavaScript.
     */
    private static Object invokeOgnlMethod(BeanHolder beanHolder, String beanName, String ognl, Exchange exchange) {

        // we must start with having bean as the result
        Object result = beanHolder.getBean();

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
        Object beanToCall = beanHolder.getBean();
        Class<?> beanType = beanHolder.getBeanInfo().getType();

        // there must be a bean to call with, we currently does not support OGNL expressions on using purely static methods
        if (beanToCall == null && beanType == null) {
            throw new IllegalArgumentException("Bean instance and bean type is null. OGNL bean expressions requires to have either a bean instance of the class name of the bean to use.");
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
                holder = new ConstantBeanHolder(beanToCall, exchange.getContext());
            } else if (beanType != null) {
                holder = new ConstantTypeBeanHolder(beanType, exchange.getContext());
            } else {
                holder = null;
            }

            // support the null safe operator
            boolean nullSafe = OgnlHelper.isNullSafeOperator(methodName);

            if (holder == null) {
                String name = getBeanName(null, beanHolder);
                throw new RuntimeBeanExpressionException(exchange, name, ognl, "last method returned null and therefore cannot continue to invoke method " + methodName + " on a null instance");
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
                if (SimpleLanguage.hasSimpleFunction(key)) {
                    key = SimpleLanguage.expression(key).evaluate(exchange, String.class);
                }
                if (key != null) {
                    result = lookupResult(resultExchange, key, result, nullSafe, ognlPath, holder.getBean());
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

    private static Object lookupResult(Exchange exchange, String key, Object result, boolean nullSafe, String ognlPath, Object bean) {
        StringHelper.notEmpty(key, "key", "in Simple language ognl path: " + ognlPath);

        // trim key
        key = key.trim();

        // remove any enclosing quotes
        key = StringHelper.removeLeadingAndEndingQuotes(key);

        // try map first
        Map<?, ?> map = exchange.getContext().getTypeConverter().convertTo(Map.class, result);
        if (map != null) {
            return map.get(key);
        }

        // special for list is last keyword
        Integer num = exchange.getContext().getTypeConverter().tryConvertTo(Integer.class, key);
        boolean checkList = key.startsWith("last") || num != null;

        if (checkList) {
            List<?> list = exchange.getContext().getTypeConverter().convertTo(List.class, result);
            if (list != null) {
                if (key.startsWith("last")) {
                    num = list.size() - 1;

                    // maybe its an expression to subtract a number after last
                    String after = StringHelper.after(key, "-");
                    if (after != null) {
                        Integer redux = exchange.getContext().getTypeConverter().tryConvertTo(Integer.class, after.trim());
                        if (redux != null) {
                            num -= redux;
                        } else {
                            throw new ExpressionIllegalSyntaxException(key);
                        }
                    }
                }
                if (num != null && num >= 0 && list.size() > num - 1 && list.size() > 0) {
                    return list.get(num);
                }
                if (!nullSafe) {
                    // not null safe then its mandatory so thrown out of bounds exception
                    throw new IndexOutOfBoundsException("Index: " + num + ", Size: " + list.size()
                            + " out of bounds with List from bean: " + bean + "using OGNL path [" + ognlPath + "]");
                }
            }
        }

        if (!nullSafe) {
            throw new IndexOutOfBoundsException("Key: " + key + " not found in bean: " + bean + " of type: "
                    + ObjectHelper.classCanonicalName(bean) + " using OGNL path [" + ognlPath + "]");
        } else {
            // null safe so we can return null
            return null;
        }
    }

}
