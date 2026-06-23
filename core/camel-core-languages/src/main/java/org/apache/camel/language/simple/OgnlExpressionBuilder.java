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
package org.apache.camel.language.simple;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Expression;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.Language;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.util.OgnlHelper;
import org.apache.camel.util.StringHelper;

/**
 * Expression builder for OGNL-based navigation functions used by the simple language.
 */
public final class OgnlExpressionBuilder {

    private OgnlExpressionBuilder() {
    }

    /**
     * Returns the expression for the exchanges inbound message header invoking methods defined in a simple OGNL
     * notation
     *
     * @param ognl methods to invoke on the header in a simple OGNL syntax
     */
    public static Expression headersOgnlExpression(final String ognl) {
        return new KeyedOgnlExpressionAdapter(
                ognl, "headerOgnl(" + ognl + ")",
                (exchange, exp) -> {
                    String text = exp.evaluate(exchange, String.class);
                    return exchange.getIn().getHeader(text);
                });
    }

    /**
     * Returns the expression for the variable invoking methods defined in a simple OGNL notation
     *
     * @param ognl methods to invoke on the variable in a simple OGNL syntax
     */
    public static Expression variablesOgnlExpression(final String ognl) {
        return new KeyedOgnlExpressionAdapter(
                ognl, "variableOgnl(" + ognl + ")",
                (exchange, exp) -> {
                    String text = exp.evaluate(exchange, String.class);
                    return ExchangeHelper.getVariable(exchange, text);
                });
    }

    /**
     * Returns the expression for the exchanges inbound message body converted to the given type and invoking methods on
     * the converted body defined in a simple OGNL notation
     */
    public static Expression mandatoryBodyOgnlExpression(final String name, final String ognl) {
        return new ExpressionAdapter() {
            private ClassResolver classResolver;
            private Expression exp;
            private Language bean;

            @Override
            public Object evaluate(Exchange exchange) {
                String text = exp.evaluate(exchange, String.class);
                Class<?> type;
                try {
                    type = classResolver.resolveMandatoryClass(text);
                } catch (ClassNotFoundException e) {
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                }
                Object body;
                try {
                    body = exchange.getIn().getMandatoryBody(type);
                } catch (InvalidPayloadException e) {
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                }
                Expression ognlExp = bean.createExpression(null, new Object[] { null, body, ognl });
                ognlExp.init(exchange.getContext());
                return ognlExp.evaluate(exchange, Object.class);
            }

            @Override
            public void init(CamelContext context) {
                classResolver = context.getClassResolver();
                exp = ExpressionBuilder.simpleExpression(name);
                exp.init(context);
                bean = context.resolveLanguage("bean");
            }

            @Override
            public String toString() {
                return "mandatoryBodyAs[" + name + "](" + ognl + ")";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body converted to the given type
     */
    public static Expression mandatoryBodyExpression(final String name) {
        return new ExpressionAdapter() {
            private ClassResolver classResolver;
            private Expression exp;

            @Override
            public Object evaluate(Exchange exchange) {
                String text = exp.evaluate(exchange, String.class);
                Class<?> type;
                try {
                    type = classResolver.resolveMandatoryClass(text);
                } catch (ClassNotFoundException e) {
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                }
                try {
                    return exchange.getIn().getMandatoryBody(type);
                } catch (InvalidPayloadException e) {
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                }
            }

            @Override
            public void init(CamelContext context) {
                classResolver = context.getClassResolver();
                exp = ExpressionBuilder.simpleExpression(name);
                exp.init(context);
            }

            @Override
            public String toString() {
                return "mandatoryBodyAs[" + name + "]";
            }
        };
    }

    /**
     * Returns the expression for the message converted to the given type and invoking methods on the converted message
     * defined in a simple OGNL notation
     */
    public static Expression messageOgnlExpression(final String name, final String ognl) {
        return new ExpressionAdapter() {
            private ClassResolver classResolver;
            private Expression exp;
            private Language bean;

            @Override
            public Object evaluate(Exchange exchange) {
                String text = exp.evaluate(exchange, String.class);
                Class<?> type;
                try {
                    type = classResolver.resolveMandatoryClass(text);
                } catch (ClassNotFoundException e) {
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                }
                Object msg = exchange.getMessage(type);
                if (msg != null) {
                    // ognl is able to evaluate method name if it contains nested functions
                    // so we should not eager evaluate ognl as a string
                    Expression ognlExp = bean.createExpression(null, new Object[] { null, msg, ognl });
                    ognlExp.init(exchange.getContext());
                    return ognlExp.evaluate(exchange, Object.class);
                } else {
                    return null;
                }
            }

            @Override
            public void init(CamelContext context) {
                classResolver = context.getClassResolver();
                exp = ExpressionBuilder.simpleExpression(name);
                exp.init(context);
                bean = context.resolveLanguage("bean");
            }

            @Override
            public String toString() {
                return "messageOgnlAs[" + name + "](" + ognl + ")";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body converted to the given type and invoking methods on
     * the converted body defined in a simple OGNL notation
     */
    public static Expression bodyOgnlExpression(final String name, final String ognl) {
        return new ExpressionAdapter() {
            private ClassResolver classResolver;
            private Expression exp;
            private Language bean;

            @Override
            public Object evaluate(Exchange exchange) {
                String text = exp.evaluate(exchange, String.class);
                Class<?> type;
                try {
                    type = classResolver.resolveMandatoryClass(text);
                } catch (ClassNotFoundException e) {
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                }
                Object body = exchange.getIn().getBody(type);
                if (body != null) {
                    // ognl is able to evaluate method name if it contains nested functions
                    // so we should not eager evaluate ognl as a string
                    Expression ognlExp = bean.createExpression(null, new Object[] { null, body, ognl });
                    ognlExp.init(exchange.getContext());
                    return ognlExp.evaluate(exchange, Object.class);
                } else {
                    return null;
                }
            }

            @Override
            public void init(CamelContext context) {
                classResolver = context.getClassResolver();
                exp = ExpressionBuilder.simpleExpression(name);
                exp.init(context);
                bean = context.resolveLanguage("bean");
            }

            @Override
            public String toString() {
                return "bodyOgnlAs[" + name + "](" + ognl + ")";
            }
        };
    }

    /**
     * Returns the expression for the exchange invoking methods defined in a simple OGNL notation
     *
     * @param ognl methods to invoke on the exchange in a simple OGNL syntax
     */
    public static Expression exchangeOgnlExpression(final String ognl) {
        return new ExpressionAdapter() {
            private Language bean;

            @Override
            public Object evaluate(Exchange exchange) {
                // ognl is able to evaluate method name if it contains nested functions
                // so we should not eager evaluate ognl as a string
                Expression ognlExp = bean.createExpression(null, new Object[] { null, exchange, ognl });
                ognlExp.init(exchange.getContext());
                return ognlExp.evaluate(exchange, Object.class);
            }

            @Override
            public void init(CamelContext context) {
                bean = context.resolveLanguage("bean");
            }

            @Override
            public String toString() {
                return "exchangeOgnl(" + ognl + ")";
            }
        };
    }

    /**
     * Returns the expression for the exchanges camelContext invoking methods defined in a simple OGNL notation
     *
     * @param ognl methods to invoke on the context in a simple OGNL syntax
     */
    public static Expression camelContextOgnlExpression(final String ognl) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                exp = ExpressionBuilder.beanExpression(context, ognl);
                exp.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                // ognl is able to evaluate method name if it contains nested functions
                // so we should not eager evaluate ognl as a string
                return exp.evaluate(exchange, Object.class);
            }

            @Override
            public String toString() {
                return "camelContextOgnl(" + ognl + ")";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body invoking methods defined in a simple OGNL notation
     *
     * @param ognl methods to invoke on the body in a simple OGNL syntax
     */
    public static Expression bodyOgnlExpression(final String ognl) {
        return new ExpressionAdapter() {
            private Language bean;

            @Override
            public Object evaluate(Exchange exchange) {
                Object body = exchange.getIn().getBody();
                if (body == null) {
                    return null;
                }
                Expression ognlExp = bean.createExpression(null, new Object[] { null, body, ognl });
                ognlExp.init(exchange.getContext());
                return ognlExp.evaluate(exchange, Object.class);
            }

            @Override
            public void init(CamelContext context) {
                bean = context.resolveLanguage("bean");
            }

            @Override
            public String toString() {
                return "bodyOgnl(" + ognl + ")";
            }
        };
    }

    /**
     * Returns an expression for the property value of exchange with the given name invoking methods defined in a simple
     * OGNL notation
     *
     * @param ognl methods to invoke on the property in a simple OGNL syntax
     */
    public static Expression propertyOgnlExpression(final String ognl) {
        return new KeyedOgnlExpressionAdapter(
                ognl, "propertyOgnl(" + ognl + ")",
                (exchange, exp) -> {
                    String text = exp.evaluate(exchange, String.class);
                    return exchange.getProperty(text);
                });
    }

    /**
     * Returns the expression for the exchange's exception invoking methods defined in a simple OGNL notation
     *
     * @param ognl methods to invoke on the body in a simple OGNL syntax
     */
    public static Expression exchangeExceptionOgnlExpression(final String ognl) {
        return new ExpressionAdapter() {
            private Language bean;

            @Override
            public Object evaluate(Exchange exchange) {
                Object exception = exchange.getException();
                if (exception == null) {
                    exception = exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Exception.class);
                }

                if (exception == null) {
                    return null;
                }

                // ognl is able to evaluate method name if it contains nested functions
                // so we should not eager evaluate ognl as a string
                Expression ognlExp = bean.createExpression(null, new Object[] { null, exception, ognl });
                ognlExp.init(exchange.getContext());
                return ognlExp.evaluate(exchange, Object.class);
            }

            @Override
            public void init(CamelContext context) {
                bean = context.resolveLanguage("bean");
            }

            @Override
            public String toString() {
                return "exchangeExceptionOgnl(" + ognl + ")";
            }
        };
    }

    /**
     * Expression adapter for OGNL expression from Message Header or Exchange property
     */
    public static class KeyedOgnlExpressionAdapter extends ExpressionAdapter {
        private final String ognl;
        private final String toStringValue;
        private final KeyedEntityRetrievalStrategy keyedEntityRetrievalStrategy;
        private String key;
        private final String method;
        private Expression keyExpression;
        private Expression ognlExpression;
        private Language beanLanguage;

        public KeyedOgnlExpressionAdapter(String ognl, String toStringValue,
                                          KeyedEntityRetrievalStrategy keyedEntityRetrievalStrategy) {
            this.ognl = ognl;
            this.toStringValue = toStringValue;
            this.keyedEntityRetrievalStrategy = keyedEntityRetrievalStrategy;

            // Split ognl except when this is not a Map, Array
            // and we would like to keep the dots within the key name
            List<String> methods = OgnlHelper.splitOgnl(ognl);

            key = methods.get(0);
            String keySuffix = "";
            // if ognl starts with a key inside brackets (eg: [foo.bar])
            // remove starting and ending brackets from key
            if (key.startsWith("[") && key.endsWith("]")) {
                key = StringHelper.removeLeadingAndEndingQuotes(key.substring(1, key.length() - 1));
                keySuffix = StringHelper.after(methods.get(0), key);
            }
            // remove any OGNL operators so we got the pure key name
            key = OgnlHelper.removeOperators(key);
            // and this may be the last remainder method to try as OGNL if there are no exchange properties with those key names
            method = StringHelper.after(ognl, key + keySuffix);
        }

        @Override
        public void init(CamelContext context) {
            beanLanguage = context.resolveLanguage("bean");
            ognlExpression = ExpressionBuilder.simpleExpression(ognl);
            ognlExpression.init(context);
            // key must be lazy eval as it only used in special situations
        }

        @Override
        public Object evaluate(Exchange exchange) {
            // try with full name first
            Object property = keyedEntityRetrievalStrategy.getKeyedEntity(exchange, ognlExpression);
            if (property != null) {
                return property;
            }

            // key must be lazy eval as it only used in special situations
            if (keyExpression == null) {
                keyExpression = ExpressionBuilder.simpleExpression(key);
                keyExpression.init(exchange.getContext());
            }

            property = keyedEntityRetrievalStrategy.getKeyedEntity(exchange, keyExpression);
            if (property == null) {
                return null;
            }
            if (method != null) {
                Expression exp = beanLanguage.createExpression(null, new Object[] { null, property, method });
                exp.init(exchange.getContext());
                return exp.evaluate(exchange, Object.class);
            } else {
                return property;
            }
        }

        @Override
        public String toString() {
            return toStringValue;
        }

        /**
         * Strategy to retrieve the value based on the key
         */
        public interface KeyedEntityRetrievalStrategy {
            Object getKeyedEntity(Exchange exchange, Expression key);
        }
    }
}
