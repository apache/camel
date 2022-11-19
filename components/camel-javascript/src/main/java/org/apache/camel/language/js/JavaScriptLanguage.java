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
import org.apache.camel.support.LanguageSupport;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

@Language("js")
public class JavaScriptLanguage extends LanguageSupport implements ScriptingLanguage {

    @Override
    public Predicate createPredicate(String expression) {
        expression = loadResource(expression);
        return new JavaScriptExpression(expression, Boolean.class);
    }

    @Override
    public Expression createExpression(String expression) {
        expression = loadResource(expression);
        return new JavaScriptExpression(expression, Object.class);
    }

    @Override
    public <T> T evaluate(String script, Map<String, Object> bindings, Class<T> resultType) {
        script = loadResource(script);

        Context cx = Context.newBuilder("js")
                .allowIO(true)
                .build();
        Value b = cx.getBindings("js");

        bindings.forEach(b::putMember);
        Value o = cx.eval("js", script);
        Object answer = o != null ? o.as(resultType) : null;
        return resultType.cast(answer);
    }
}
