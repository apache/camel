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

package org.apache.camel.language.python;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Expression;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.Predicate;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.TypedLanguageSupport;
import org.python.core.PyCode;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;

@Language("python")
public class PythonLanguage extends TypedLanguageSupport implements ScriptingLanguage {

    private final Map<String, PyCode> compiledScriptsCache;

    private final PythonInterpreter compiler = new PythonInterpreter();

    private PythonLanguage(Map<String, PyCode> compiledScriptsCache) {
        this.compiledScriptsCache = compiledScriptsCache;
    }

    public PythonLanguage() {
        this(LRUCacheFactory.newLRUSoftCache(16, 1000, true));
    }

    @Override
    public Predicate createPredicate(String expression) {
        return createPythonExpression(expression, Boolean.class);
    }

    @Override
    public Expression createExpression(String expression) {
        return createPythonExpression(expression, Object.class);
    }

    private PythonExpression createPythonExpression(String expression, Class<?> type) {
        return new PythonExpression(loadResource(expression), type);
    }

    @Override
    public <T> T evaluate(String script, Map<String, Object> bindings, Class<T> resultType) {
        script = loadResource(script);

        PyCode code = getCompiledScriptFromCache(script);

        if (code == null) {
            try {
                code = compiler.compile(script);
                addCompiledScriptToCache(script, code);
            } catch (Exception e) {
                throw new ExpressionIllegalSyntaxException(script, e);
            }
        }

        try {
            if (bindings != null) {
                bindings.forEach(compiler::set);
            }
            PyObject out = compiler.eval(code);
            if (out != null) {
                String value = out.toString();
                return getCamelContext().getTypeConverter().convertTo(resultType, value);
            }
        } catch (Exception e) {
            throw new ExpressionIllegalSyntaxException(script, e);
        } finally {
            compiler.cleanup();
        }
        return null;
    }

    private void addCompiledScriptToCache(String script, PyCode compiledScript) {
        compiledScriptsCache.put(script, compiledScript);
    }

    private PyCode getCompiledScriptFromCache(String script) {
        return compiledScriptsCache.get(script);
    }

    public static class Builder {
        private final Map<String, PyCode> cache = new HashMap<>();

        public void addScript(String script, PyCode compiledScript) {
            cache.put(script, compiledScript);
        }

        public PythonLanguage build() {
            return new PythonLanguage(Collections.unmodifiableMap(cache));
        }
    }
}
