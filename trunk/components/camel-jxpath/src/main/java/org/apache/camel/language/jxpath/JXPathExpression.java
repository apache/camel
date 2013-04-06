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
package org.apache.camel.language.jxpath;

import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.support.ExpressionSupport;
import org.apache.commons.jxpath.CompiledExpression;
import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.JXPathException;

/**
 * <a href="http://commons.apache.org/jxpath/">JXPath</a> {@link org.apache.camel.Expression} support
 */
public class JXPathExpression extends ExpressionSupport {

    private final String expression;
    private CompiledExpression compiledExpression;
    private final Class<?> type;
    private boolean lenient;

    /**
     * Creates a new JXPathExpression instance (lenient is disabled)
     * 
     * @param expression the JXPath expression to be evaluated
     * @param type the expected result type
     */
    public JXPathExpression(String expression, Class<?> type) {
        this(expression, type, false);
    }

    /**
     * Creates a new JXPathExpression instance
     *
     * @param expression the JXPath expression to be evaluated
     * @param type the expected result type
     * @param lenient to configure lenient
     */
    public JXPathExpression(String expression, Class<?> type, boolean lenient) {
        this.expression = expression;
        this.type = type;
        this.lenient = lenient;
    }

    public boolean isLenient() {
        return lenient;
    }

    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    public <T> T evaluate(Exchange exchange, Class<T> tClass) {
        try {
            JXPathContext context = JXPathContext.newContext(exchange);
            context.setLenient(lenient);
            Object result = getJXPathExpression().getValue(context, type);
            assertResultType(exchange, result);
            return exchange.getContext().getTypeConverter().convertTo(tClass, result);
        } catch (JXPathException e) {
            throw new ExpressionEvaluationException(this, exchange, e);
        }
    }

    /*
     * Check if the result is of the specified type
     */
    private void assertResultType(Exchange exchange, Object result) {
        if (result != null && !type.isAssignableFrom(result.getClass())) {
            throw new JXPathException("JXPath result type is " + result.getClass() + " instead of required type " + type);
        }
    }

    @Override
    protected String assertionFailureMessage(Exchange exchange) {
        return expression;
    }

    /*
     * Get a compiled expression instance for better performance
     */
    private synchronized CompiledExpression getJXPathExpression() {
        if (compiledExpression == null) {
            compiledExpression = JXPathContext.compile(expression);
        }
        return compiledExpression;
    }

    @Override
    public String toString() {
        return "JXpath[" + expression + "]";
    }
}
