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
package org.apache.camel.language.mvel;

import java.io.Serializable;
import java.util.Map;

import org.apache.camel.Expression;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.Predicate;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.TypedLanguageSupport;
import org.mvel2.MVEL;

/**
 * An <a href="http://mvel.codehaus.org/">MVEL</a> {@link org.apache.camel.spi.Language} plugin
 */
@Language("mvel")
@BodyTypes(expressionIn = @BodyType(value = Carrier.ANY,
                                    description = "The body as it is, in request.body or message.body: there is no variable"
                                                  + " body"),
           expressionOut = @BodyType(value = Carrier.UNKNOWN, description = "What the expression returns"))
public class MvelLanguage extends TypedLanguageSupport implements ScriptingLanguage {

    @Override
    public Predicate createPredicate(String expression) {
        return createMvelExpression(expression, Boolean.class);
    }

    @Override
    public Expression createExpression(String expression) {
        return createMvelExpression(expression, Object.class);
    }

    private MvelExpression createMvelExpression(String expression, Class<?> type) {
        return new MvelExpression(loadResource(expression), type);
    }

    @Override
    public <T> T evaluate(String script, Map<String, Object> bindings, Class<T> resultType) {
        script = loadResource(script);
        try {
            Serializable compiled = MVEL.compileExpression(script);
            Object value = MVEL.executeExpression(compiled, bindings);
            return getCamelContext().getTypeConverter().convertTo(resultType, value);
        } catch (Exception e) {
            throw new ExpressionIllegalSyntaxException(script, e);
        }
    }
}
