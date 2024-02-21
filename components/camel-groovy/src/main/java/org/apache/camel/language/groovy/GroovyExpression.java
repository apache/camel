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
package org.apache.camel.language.groovy;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.apache.camel.Exchange;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ExpressionSupport;
import org.apache.camel.support.ObjectHelper;

public class GroovyExpression extends ExpressionSupport {
    private final String text;

    public GroovyExpression(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return "groovy: " + text;
    }

    @Override
    protected String assertionFailureMessage(Exchange exchange) {
        return "groovy: " + text;
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        Map<String, Object> globalVariables = new HashMap<>();
        Script script = instantiateScript(exchange, globalVariables);
        script.setBinding(createBinding(exchange, globalVariables));
        Object value = script.run();

        return exchange.getContext().getTypeConverter().convertTo(type, value);
    }

    @SuppressWarnings("unchecked")
    private Script instantiateScript(Exchange exchange, Map<String, Object> globalVariables) {
        // Get the script from the cache, or create a new instance
        GroovyLanguage language = (GroovyLanguage) exchange.getContext().resolveLanguage("groovy");
        Set<GroovyShellFactory> shellFactories = exchange.getContext().getRegistry().findByType(GroovyShellFactory.class);
        GroovyShellFactory shellFactory = null;
        String fileName = null;
        if (shellFactories.size() == 1) {
            shellFactory = shellFactories.iterator().next();
            fileName = shellFactory.getFileName(exchange);
            globalVariables.putAll(shellFactory.getVariables(exchange));
        }
        final String key = fileName != null ? fileName + text : text;
        Class<Script> scriptClass = language.getScriptFromCache(key);
        if (scriptClass == null) {
            ClassLoader cl = exchange.getContext().getApplicationContextClassLoader();
            GroovyShell shell = shellFactory != null ? shellFactory.createGroovyShell(exchange)
                    : cl != null ? new GroovyShell(cl) : new GroovyShell();
            scriptClass = fileName != null
                    ? shell.getClassLoader().parseClass(text, fileName) : shell.getClassLoader().parseClass(text);
            language.addScriptToCache(key, scriptClass);
        }
        // New instance of the script
        return ObjectHelper.newInstance(scriptClass, Script.class);
    }

    private Binding createBinding(Exchange exchange, Map<String, Object> globalVariables) {
        Map<String, Object> variables = new HashMap<>(globalVariables);
        ExchangeHelper.populateVariableMap(exchange, variables, true);
        return new Binding(variables);
    }
}
