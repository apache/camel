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
package org.apache.camel.builder;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.ExpressionResultTypeAware;
import org.apache.camel.spi.Language;

/**
 * Creates a DataSonnet language builder.
 * <p/>
 * This builder is available in the Java DSL from the {@link RouteBuilder} which means that using datasonnet language
 * for {@link Expression}s or {@link Predicate}s is very easy with the help of this builder.
 */
@Deprecated
public class DatasonnetBuilder implements Predicate, Expression, ExpressionResultTypeAware {

    private String text;
    private Class<?> resultType;
    private String bodyMediaType;
    private String outputMediaType;

    // cache the expression/predicate
    private Language datasonnet;
    private volatile Expression expression;
    private volatile Predicate predicate;

    public DatasonnetBuilder(String text) {
        this.text = text;
    }

    public static DatasonnetBuilder datasonnet(String text) {
        return new DatasonnetBuilder(text);
    }

    public static DatasonnetBuilder datasonnet(String text, Class<?> resultType) {
        DatasonnetBuilder answer = datasonnet(text);
        answer.setResultType(resultType);
        return answer;
    }

    public String getText() {
        return text;
    }

    @Override
    public String getExpressionText() {
        return getText();
    }

    @Override
    public Class<?> getResultType() {
        return resultType;
    }

    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public String getBodyMediaType() {
        return bodyMediaType;
    }

    public void setBodyMediaType(String bodyMediaType) {
        this.bodyMediaType = bodyMediaType;
    }

    public String getOutputMediaType() {
        return outputMediaType;
    }

    public void setOutputMediaType(String outputMediaType) {
        this.outputMediaType = outputMediaType;
    }

    public DatasonnetBuilder resultType(Class<?> resultType) {
        setResultType(resultType);
        return this;
    }

    public DatasonnetBuilder bodyMediaType(String bodyMediaType) {
        setBodyMediaType(bodyMediaType);
        return this;
    }

    public DatasonnetBuilder outputMediaType(String outputMediaType) {
        setOutputMediaType(outputMediaType);
        return this;
    }

    @Override
    public String toString() {
        return text;
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        if (expression == null) {
            if (datasonnet == null) {
                init(exchange.getContext());
            }
            if (resultType != null) {
                Object[] properties = new Object[3];
                properties[0] = resultType;
                properties[1] = bodyMediaType;
                properties[2] = outputMediaType;
                expression = datasonnet.createExpression(text, properties);
            } else {
                expression = datasonnet.createExpression(text);
            }
            expression.init(exchange.getContext());
        }
        return expression.evaluate(exchange, type);
    }

    @Override
    public boolean matches(Exchange exchange) {
        if (predicate == null) {
            if (datasonnet == null) {
                init(exchange.getContext());
            }
            predicate = datasonnet.createPredicate(text);
            predicate.init(exchange.getContext());
        }
        return predicate.matches(exchange);
    }

    @Override
    public void init(CamelContext context) {
        datasonnet = context.resolveLanguage("datasonnet");
        text = context.resolvePropertyPlaceholders(text);
    }
}
