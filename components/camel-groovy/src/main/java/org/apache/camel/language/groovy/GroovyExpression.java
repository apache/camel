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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.apache.camel.Exchange;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.attachment.DefaultAttachmentMessage;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ExpressionSupport;
import org.apache.camel.support.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroovyExpression extends ExpressionSupport {

    private static final Logger LOG = LoggerFactory.getLogger(GroovyExpression.class);

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

        Object value;
        try {
            value = script.run();
        } catch (groovy.lang.MissingPropertyException e) {
            // a bean name used as a variable (formatter.format(body)): say where beans are, and what the variables are.
            // The same exception type with the hint in its message; no cause, as the message would then be built from
            // the cause and lose the hint
            String name = e.getProperty();
            boolean bean = name != null && exchange.getContext().getRegistry().lookupByName(name) != null;
            String hint = bean
                    ? "'" + name + "' is a bean in the registry, not a script variable; use"
                      + " exchange.getContext().getRegistry().lookupByName('" + name + "'), or call it from the route with"
                      + " - bean: {ref: " + name + "}"
                    : "the script variables are exchange, message, body, headers, variables, exchangeProperties,"
                      + " camelContext, request and log";
            throw new groovy.lang.MissingPropertyException(
                    e.getMessageWithoutLocationText() + " (" + hint + ")", name, e.getType());
        }

        return exchange.getContext().getTypeConverter().convertTo(type, value);
    }

    @SuppressWarnings("unchecked")
    protected Script instantiateScript(Exchange exchange, Map<String, Object> globalVariables) {
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
            // prefer to use classloader from groovy script compiler, and if not fallback to app context
            ClassLoader cl = exchange.getContext().getCamelContextExtension().getContextPlugin(GroovyScriptClassLoader.class);
            GroovyShell shell = shellFactory != null ? shellFactory.createGroovyShell(exchange)
                    : cl != null ? new GroovyShell(cl) : new GroovyShell();
            scriptClass = fileName != null
                    ? shell.getClassLoader().parseClass(text, fileName) : shell.getClassLoader().parseClass(text);
            language.addScriptToCache(key, scriptClass);
        }
        // New instance of the script
        return ObjectHelper.newInstance(scriptClass, Script.class);
    }

    protected Binding createBinding(Exchange exchange, Map<String, Object> globalVariables) {
        Map<String, Object> map = new HashMap<>(globalVariables);
        ExchangeHelper.populateVariableMap(exchange, map, true);
        AttachmentMessage am = new DefaultAttachmentMessage(exchange.getMessage());
        if (am.hasAttachments()) {
            map.put("attachments", am.getAttachments());
        } else {
            map.put("attachments", Collections.EMPTY_MAP);
        }
        map.put("log", LOG);
        return new Binding(map);
    }
}
