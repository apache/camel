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
package org.apache.camel.support;

import org.apache.camel.Expression;
import org.apache.camel.spi.Language;
import org.apache.camel.support.builder.ExpressionBuilder;

/**
 * Base class for {@link Language} implementations that support a result type and different sources of input data.
 */
public abstract class SingleInputTypedLanguageSupport extends TypedLanguageSupport {

    /**
     * Name of header to use as input, instead of the message body
     */
    private String headerName;
    /**
     * Name of property to use as input, instead of the message body.
     * <p>
     * It has a lower precedent than the name of header if both are set.
     */
    private String propertyName;

    public String getHeaderName() {
        return headerName;
    }

    /**
     * Name of header to use as input, instead of the message body
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    /**
     * Name of property to use as input, instead of the message body.
     * <p>
     * It has a lower precedent than the name of header if both are set.
     */
    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        Class<?> type = property(Class.class, properties, 0, getResultType());
        String header = property(String.class, properties, 1, getHeaderName());
        String property = property(String.class, properties, 2, getPropertyName());
        Expression source = ExpressionBuilder.singleInputExpression(header, property);
        if (type == null || type == Object.class) {
            return createExpression(source, expression, properties);
        }
        return ExpressionBuilder.convertToExpression(createExpression(source, expression, properties), type);
    }

    /**
     * Creates an expression based on the input with properties.
     *
     * @param  source     the expression allowing to retrieve the input data of the main expression.
     * @param  expression the main expression to evaluate.
     * @param  properties configuration properties (optimized as object array with hardcoded positions for properties)
     * @return            the created expression
     */
    protected Expression createExpression(Expression source, String expression, Object[] properties) {
        throw new UnsupportedOperationException();
    }
}
