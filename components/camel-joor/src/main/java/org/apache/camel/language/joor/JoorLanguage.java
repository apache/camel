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
package org.apache.camel.language.joor;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.LanguageSupport;

@Language("joor")
public class JoorLanguage extends LanguageSupport {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private Class<?> resultType;
    private boolean singleQuotes = true;

    public Class<?> getResultType() {
        return resultType;
    }

    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public boolean isSingleQuotes() {
        return singleQuotes;
    }

    public void setSingleQuotes(boolean singleQuotes) {
        this.singleQuotes = singleQuotes;
    }

    @Override
    public Predicate createPredicate(String expression) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression));
    }

    @Override
    public Expression createExpression(String expression) {
        JoorExpression exp = new JoorExpression(nextFQN(), expression);
        exp.setResultType(resultType);
        exp.setSingleQuotes(singleQuotes);
        exp.init(getCamelContext());
        return exp;
    }

    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        return (JoorExpression) createExpression(expression, properties);
    }

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        JoorExpression exp = new JoorExpression(nextFQN(), expression);
        exp.setResultType(property(Class.class, properties, 0, resultType));
        exp.setSingleQuotes(property(boolean.class, properties, 1, singleQuotes));
        exp.init(getCamelContext());
        return exp;
    }

    static String nextFQN() {
        return "org.apache.camel.language.joor.compiled.JoorLanguage" + COUNTER.incrementAndGet();
    }
}
