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

import java.util.Map;

import ognl.ClassResolver;
import ognl.Ognl;
import ognl.OgnlContext;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.Predicate;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.TypedLanguageSupport;

@Language("ognl")
public class OgnlLanguage extends TypedLanguageSupport implements ScriptingLanguage {

    @Override
    public Predicate createPredicate(String expression) {
        return createOgnlExpression(expression, Boolean.class);
    }

    @Override
    public Expression createExpression(String expression) {
        return createOgnlExpression(expression, Object.class);
    }

    private OgnlExpression createOgnlExpression(String expression, Class<?> type) {
        return new OgnlExpression(loadResource(expression), type);
    }

    @Override
    public <T> T evaluate(String script, Map<String, Object> bindings, Class<T> resultType) {
        script = loadResource(script);
        try {
            Object compiled = Ognl.parseExpression(script);
            ClassResolver cr = new CamelClassResolver(getCamelContext().getClassResolver());
            OgnlContext oglContext = Ognl.createDefaultContext(null, cr);
            Object value = Ognl.getValue(compiled, oglContext, bindings);
            return getCamelContext().getTypeConverter().convertTo(resultType, value);
        } catch (Exception e) {
            throw new ExpressionIllegalSyntaxException(script, e);
        }
    }
}
