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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.component.bean.BeanHolder;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.component.bean.ConstantBeanHolder;
import org.apache.camel.component.bean.RegistryBean;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;

/**
 * Evaluates an expression using a bean method invocation
 *
 * @version 
 */
public class BeanExpression implements Expression, Predicate {
    private String beanName;
    private String method;
    private Object bean;

    public BeanExpression(Object bean, String method) {
        this.bean = bean;
        this.method = method;
    }

    public BeanExpression(String beanName, String method) {
        this.beanName = beanName;
        this.method = method;
    }

    @Override
    public String toString() {
        return "BeanExpression[bean:" + (bean == null ? beanName : bean) + " method: " + method + "]";
    }

    public Object evaluate(Exchange exchange) {
        // either use registry lookup or a constant bean
        BeanHolder holder;
        if (bean == null) {
            holder = new RegistryBean(exchange.getContext(), beanName);
        } else {
            holder = new ConstantBeanHolder(bean, exchange.getContext());
        }

        // invoking the bean can either be the easy way or using OGNL

        // validate OGNL
        if (OgnlHelper.isInvalidValidOgnlExpression(method)) {
            ExpressionIllegalSyntaxException cause = new ExpressionIllegalSyntaxException(method);
            throw new RuntimeBeanExpressionException(exchange, beanName, method, cause);
        }

        if (OgnlHelper.isValidOgnlExpression(method)) {
            // okay the method is an ognl expression
            Object beanToCall = holder.getBean();
            OgnlInvokeProcessor ognl = new OgnlInvokeProcessor(beanToCall, method);
            try {
                ognl.process(exchange);
                return ognl.getResult();
            } catch (Exception e) {
                throw new RuntimeBeanExpressionException(exchange, beanName, method, e);
            }
        } else {
            // regular non ognl invocation
            InvokeProcessor invoke = new InvokeProcessor(holder, method);
            try {
                invoke.process(exchange);
                return invoke.getResult();
            } catch (Exception e) {
                throw new RuntimeBeanExpressionException(exchange, beanName, method, e);
            }
        }
    }

    public <T> T evaluate(Exchange exchange, Class<T> type) {
        Object result = evaluate(exchange);
        return exchange.getContext().getTypeConverter().convertTo(type, result);
    }

    public boolean matches(Exchange exchange) {
        Object value = evaluate(exchange);
        return ObjectHelper.evaluateValuePredicate(value);
    }

    /**
     * Invokes a given bean holder. The method name is optional.
     */
    private final class InvokeProcessor implements Processor {

        private BeanHolder beanHolder;
        private String methodName;
        private Object result;

        private InvokeProcessor(BeanHolder beanHolder, String methodName) {
            this.beanHolder = beanHolder;
            this.methodName = methodName;
        }

        public void process(Exchange exchange) throws Exception {
            BeanProcessor processor = new BeanProcessor(beanHolder);
            if (methodName != null) {
                processor.setMethod(methodName);
                // enable OGNL like invocation
                processor.setShorthandMethod(true);
            }
            try {
                // copy the original exchange to avoid side effects on it
                Exchange resultExchange = exchange.copy();
                // force to use InOut to retrieve the result on the OUT message
                resultExchange.setPattern(ExchangePattern.InOut);
                processor.process(resultExchange);
                result = resultExchange.getOut().getBody();

                // propagate exceptions
                if (resultExchange.getException() != null) {
                    exchange.setException(resultExchange.getException());
                }
            } catch (Exception e) {
                throw new RuntimeBeanExpressionException(exchange, beanName, methodName, e);
            }
        }

        public Object getResult() {
            return result;
        }
    }

    /**
     * To invoke a bean using a OGNL notation which denotes the chain of methods to invoke.
     * <p/>
     * For more advanced OGNL you may have to look for a real framework such as OGNL, Mvel or dynamic
     * programming language such as Groovy, JuEL, JavaScript.
     */
    private final class OgnlInvokeProcessor implements Processor {

        private final Object bean;
        private final String ognl;
        private Object result;

        public OgnlInvokeProcessor(Object bean, String ognl) {
            this.bean = bean;
            this.ognl = ognl;
            // we must start with having bean as the result
            this.result = bean;
        }

        public void process(Exchange exchange) throws Exception {
            // copy the original exchange to avoid side effects on it
            Exchange resultExchange = exchange.copy();
            // force to use InOut to retrieve the result on the OUT message
            resultExchange.setPattern(ExchangePattern.InOut);

            // current ognl path as we go along
            String ognlPath = "";

            // loop and invoke each method
            Object beanToCall = bean;

            // Split ognl except
            // when this is not a Map, Array
            // and we would like to keep the dots
            // within the key name
            List<String> methods;

            if (ognl.startsWith("[") && ognl.endsWith("]")) {
               methods = new ArrayList<String>();
               methods.add(ognl);
            } else {
               methods = OgnlHelper.splitOgnl(ognl);
            }

            for (String methodName : methods) {
                BeanHolder holder = new ConstantBeanHolder(beanToCall, exchange.getContext());

                // support the null safe operator
                boolean nullSafe = OgnlHelper.isNullSafeOperator(methodName);

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
                    InvokeProcessor invoke = new InvokeProcessor(holder, methodName);
                    invoke.process(resultExchange);

                    // check for exception and rethrow if we failed
                    if (resultExchange.getException() != null) {
                        throw new RuntimeBeanExpressionException(exchange, beanName, methodName, resultExchange.getException());
                    }

                    result = invoke.getResult();
                }

                // if there was a key then we need to lookup using the key
                if (key != null) {
                    result = lookupResult(resultExchange, key, result, nullSafe, ognlPath, holder.getBean());
                }

                // check null safe for null results
                if (result == null && nullSafe) {
                    return;
                }

                // prepare for next bean to invoke
                beanToCall = result;
            }
        }

        private Object lookupResult(Exchange exchange, String key, Object result, boolean nullSafe, String ognlPath, Object bean) {
            // trim key
            key = key.trim();

            // try map first
            Map map = exchange.getContext().getTypeConverter().convertTo(Map.class, result);
            if (map != null) {
                return map.get(key);
            }

            // special for list is last keyword
            Integer num = exchange.getContext().getTypeConverter().convertTo(Integer.class, key);
            boolean checkList = key.startsWith("last") || num != null;

            if (checkList) {
                List list = exchange.getContext().getTypeConverter().convertTo(List.class, result);
                if (list != null) {
                    if (key.startsWith("last")) {
                        num = list.size() - 1;

                        // maybe its an expression to subtract a number after last
                        String after = ObjectHelper.after(key, "-");
                        if (after != null) {
                            Integer redux = exchange.getContext().getTypeConverter().convertTo(Integer.class, after.trim());
                            if (redux != null) {
                                num -= redux;
                            } else {
                                throw new ExpressionIllegalSyntaxException(key);
                            }
                        }
                    }
                    if (num != null && num >= 0 && list.size() > num - 1) {
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

        public Object getResult() {
            return result;
        }
    }

}
