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
package org.apache.camel.language.ognl;

import ognl.ClassResolver;
import ognl.Ognl;
import ognl.OgnlContext;
import ognl.OgnlException;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.support.ExpressionSupport;

/**
 * An OGNL {@link org.apache.camel.Expression}
 */
public class OgnlExpression extends ExpressionSupport {

    private final String expressionString;
    private final Class<?> type;
    private Object expression;

    public OgnlExpression(String expressionString, Class<?> type) {
        this.expressionString = expressionString;
        this.type = type;
        try {
            this.expression = Ognl.parseExpression(expressionString);
        } catch (OgnlException e) {
            throw new ExpressionIllegalSyntaxException(expressionString, e);
        }
    }

    public static OgnlExpression ognl(String expression) {
        return new OgnlExpression(expression, Object.class);
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> tClass) {
        ClassResolver cr = new CamelClassResolver(exchange.getContext().getClassResolver());
        RootObject root = new RootObject(exchange);
        OgnlContext oglContext = Ognl.createDefaultContext(root, cr);
        try {
            Object value = Ognl.getValue(expression, oglContext, root);
            return exchange.getContext().getTypeConverter().convertTo(tClass, value);
        } catch (OgnlException e) {
            throw new ExpressionEvaluationException(this, exchange, e);
        }
    }

    public Class<?> getType() {
        return type;
    }

    @Override
    protected String assertionFailureMessage(Exchange exchange) {
        return expressionString;
    }

    @Override
    public String toString() {
        return "OGNL[" + expressionString + "]";
    }
}
