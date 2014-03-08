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
package org.apache.camel.language.spel;

import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.impl.ExpressionSupport;
import org.apache.camel.spring.SpringCamelContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * Class responsible for evaluating <a href="http://static.springsource.org
 * /spring/docs/current/spring-framework-reference/html/expressions.html">
 * Spring Expression Language</a> in the context of Camel.
 */
@SuppressWarnings("deprecation")
public class SpelExpression extends ExpressionSupport {

    private final String expressionString;
    private final Class<?> type;

    // SpelExpressionParser is thread-safe according to the docs
    private final SpelExpressionParser expressionParser;

    public SpelExpression(String expressionString, Class<?> type) {
        this.expressionString = expressionString;
        this.type = type;
        this.expressionParser = new SpelExpressionParser();
    }

    public static SpelExpression spel(String expression) {
        return new SpelExpression(expression, Object.class);
    }

    public <T> T evaluate(Exchange exchange, Class<T> tClass) {
        try {
            Expression expression = parseExpression();
            EvaluationContext evaluationContext = createEvaluationContext(exchange);
            Object value = expression.getValue(evaluationContext);
            // Let Camel handle the type conversion
            return exchange.getContext().getTypeConverter().convertTo(tClass, value);
        } catch (Exception e) {
            throw new ExpressionEvaluationException(this, exchange, e);
        }
    }

    private EvaluationContext createEvaluationContext(Exchange exchange) {
        StandardEvaluationContext evaluationContext = new StandardEvaluationContext(new RootObject(exchange));
        if (exchange.getContext() instanceof SpringCamelContext) {
            // Support references (like @foo) in expressions to beans defined in the Registry/ApplicationContext
            ApplicationContext applicationContext = ((SpringCamelContext) exchange.getContext()).getApplicationContext();
            evaluationContext.setBeanResolver(new BeanFactoryResolver(applicationContext));
        }
        return evaluationContext;
    }

    private Expression parseExpression() {
        // Support template parsing with #{ } delimiters
        ParserContext parserContext = new TemplateParserContext();
        Expression expression = expressionParser.parseExpression(expressionString, parserContext);
        return expression;
    }

    public Class<?> getType() {
        return type;
    }

    protected String assertionFailureMessage(Exchange exchange) {
        return expressionString;
    }

    @Override
    public String toString() {
        return "SpelExpression[" + expressionString + "]";
    }
}
