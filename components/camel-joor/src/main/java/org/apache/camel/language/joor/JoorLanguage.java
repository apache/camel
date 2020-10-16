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

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.StaticService;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Language("joor")
public class JoorLanguage extends LanguageSupport implements StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(JoorLanguage.class);
    private long taken;

    private boolean preCompile = true;
    private Class<?> resultType;
    private boolean singleQuotes = true;

    public boolean isPreCompile() {
        return preCompile;
    }

    public void setPreCompile(boolean preCompile) {
        this.preCompile = preCompile;
    }

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
        JoorExpression exp = new JoorExpression(expression);
        exp.setResultType(resultType);
        exp.setSingleQuotes(singleQuotes);

        StopWatch watch = new StopWatch();
        exp.init(getCamelContext());
        taken += watch.taken();

        return exp;
    }

    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        return (JoorExpression) createExpression(expression, properties);
    }

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        JoorExpression exp = new JoorExpression(expression);
        exp.setPreCompile(property(boolean.class, properties, 0, preCompile));
        exp.setResultType(property(Class.class, properties, 1, resultType));
        exp.setSingleQuotes(property(boolean.class, properties, 2, singleQuotes));
        exp.init(getCamelContext());
        return exp;
    }

    @Override
    public void start() {
        // noop
    }

    @Override
    public void stop() {
        if (taken > 0) {
            LOG.info("jOOR language compilations took {} millis", taken);
        }
    }
}
