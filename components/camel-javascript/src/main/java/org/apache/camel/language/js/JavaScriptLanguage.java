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
package org.apache.camel.language.js;

import java.util.Map;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.TypedLanguageSupport;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import static org.graalvm.polyglot.Source.newBuilder;

@Language("js")
public class JavaScriptLanguage extends TypedLanguageSupport implements ScriptingLanguage {

    @Override
    public Predicate createPredicate(String expression) {
        return createJavaScriptExpression(expression, Boolean.class);
    }

    @Override
    public Expression createExpression(String expression) {
        return createJavaScriptExpression(expression, Object.class);
    }

    @Override
    public <T> T evaluate(String script, Map<String, Object> bindings, Class<T> resultType) {
        script = loadResource(script);
        try (Context cx = JavaScriptHelper.newContext()) {
            Value b = cx.getBindings("js");
            bindings.forEach(b::putMember);
            Source source = newBuilder("js", script, "Unnamed")
                    .mimeType("application/javascript+module").buildLiteral();
            Value o = cx.eval(source);
            Object answer = o != null ? o.as(resultType) : null;
            return resultType.cast(answer);
        }
    }

    /**
     * @param  expression the expression to evaluate
     * @param  type       the type of the result
     * @return            the corresponding {@code JavaScriptExpression}
     */
    private JavaScriptExpression createJavaScriptExpression(String expression, Class<?> type) {
        return new JavaScriptExpression(loadResource(expression), type);
    }
}
