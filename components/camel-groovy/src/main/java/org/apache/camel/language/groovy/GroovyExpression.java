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
package org.apache.camel.language.groovy;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.ExpressionSupport;
import org.apache.camel.util.ExchangeHelper;

/**
 * @version 
 */
public class GroovyExpression extends ExpressionSupport {
    private final String text;

    public GroovyExpression(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return "groovy: " + text;
    }

    protected String assertionFailureMessage(Exchange exchange) {
        return "groovy: " + text;
    }

    public <T> T evaluate(Exchange exchange, Class<T> type) {
        Script script = instantiateScript(exchange);
        script.setBinding(createBinding(exchange));
        Object value = script.run();

        return exchange.getContext().getTypeConverter().convertTo(type, value);
    }

    @SuppressWarnings("unchecked")
    private Script instantiateScript(Exchange exchange) {
        // Get the script from the cache, or create a new instance
        GroovyLanguage language = (GroovyLanguage) exchange.getContext().resolveLanguage("groovy");
        Class<Script> scriptClass = language.getScriptFromCache(text);
        if (scriptClass == null) {
            GroovyShell shell;
            Set<GroovyShellFactory> shellFactories = exchange.getContext().getRegistry().findByType(GroovyShellFactory.class);
            if (shellFactories.size() > 1) {
                throw new IllegalStateException("Too many GroovyShellFactory instances found: " + shellFactories.size());
            } else if (shellFactories.size() == 1) {
                shell = shellFactories.iterator().next().createGroovyShell(exchange);
            } else {
                ClassLoader cl = exchange.getContext().getApplicationContextClassLoader();
                shell = cl != null ? new GroovyShell(cl) : new GroovyShell();
            }
            scriptClass = shell.getClassLoader().parseClass(text);
            language.addScriptToCache(text, scriptClass);
        }

        // New instance of the script
        try {
            return scriptClass.newInstance();
        } catch (InstantiationException e) {
            throw new RuntimeCamelException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeCamelException(e);
        }
    }

    private Binding createBinding(Exchange exchange) {
        Map<String, Object> variables = new HashMap<String, Object>();
        ExchangeHelper.populateVariableMap(exchange, variables);
        return new Binding(variables);
    }
}
