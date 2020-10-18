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
import org.apache.camel.support.service.ServiceHelper;

@Language("joor")
public class JoorLanguage extends LanguageSupport implements StaticService {

    private static Boolean java8;
    private final JoorCompiler compiler = new JoorCompiler();

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
        exp.setCompiler(compiler);
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
        JoorExpression exp = new JoorExpression(expression);
        exp.setCompiler(compiler);
        exp.setPreCompile(property(boolean.class, properties, 0, preCompile));
        exp.setResultType(property(Class.class, properties, 1, resultType));
        exp.setSingleQuotes(property(boolean.class, properties, 2, singleQuotes));
        exp.init(getCamelContext());
        return exp;
    }

    @Override
    public void start() {
        if (java8 == null) {
            java8 = getJavaMajorVersion() == 8;
            if (java8) {
                throw new UnsupportedOperationException("Java 8 is not supported. Use Java 11 or higher");
            }
        }
        ServiceHelper.startService(compiler);
    }

    @Override
    public void stop() {
        ServiceHelper.stopService(compiler);
    }

    private static int getJavaMajorVersion() {
        String javaSpecVersion = System.getProperty("java.specification.version");
        return javaSpecVersion.contains(".")
                ? Integer.parseInt(javaSpecVersion.split("\\.")[1]) : Integer.parseInt(javaSpecVersion);
    }

}
