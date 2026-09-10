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
import java.util.Objects;
import java.util.Set;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.attachment.DefaultAttachmentMessage;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ExpressionSupport;
import org.apache.camel.support.LanguageHelper;
import org.apache.camel.support.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroovyExpression extends ExpressionSupport {

    private static final Logger LOG = LoggerFactory.getLogger(GroovyExpression.class);

    private final String text;

    // the language and shell factory of the CamelContext, resolved once instead of on every evaluation
    private volatile Resolved resolved;
    // the compiled script of this expression, so a cache eviction in the language does not force a recompilation
    private volatile CompiledScript compiled;

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
    public void init(CamelContext context) {
        super.init(context);
        resolve(context);
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
    protected Script instantiateScript(Exchange exchange, Map<String, Object> globalVariables) {
        Resolved r = resolve(exchange.getContext());
        GroovyShellFactory shellFactory = r.shellFactory;
        String fileName = null;
        if (shellFactory != null) {
            fileName = shellFactory.getFileName(exchange);
            globalVariables.putAll(shellFactory.getVariables(exchange));
        }

        int generation = r.language.getGeneration();
        CompiledScript c = compiled;
        Class<Script> scriptClass = null;
        if (c != null && c.generation == generation && c.context == r.context && c.language == r.language
                && Objects.equals(c.fileName, fileName)) {
            scriptClass = c.scriptClass;
        }
        if (scriptClass == null) {
            // Get the script from the cache, or create a new instance
            final String key = fileName != null ? fileName + text : text;
            scriptClass = r.language.getScriptFromCache(key);
            if (scriptClass == null) {
                // prefer to use classloader from groovy script compiler, and if not fallback to app context
                ClassLoader cl
                        = exchange.getContext().getCamelContextExtension().getContextPlugin(GroovyScriptClassLoader.class);
                GroovyShell shell = shellFactory != null ? shellFactory.createGroovyShell(exchange)
                        : cl != null ? new GroovyShell(cl) : new GroovyShell();
                scriptClass = fileName != null
                        ? shell.getClassLoader().parseClass(text, fileName) : shell.getClassLoader().parseClass(text);
                r.language.addScriptToCache(key, scriptClass);
            }
            compiled = new CompiledScript(r.context, r.language, generation, fileName, scriptClass);
        }
        // New instance of the script
        return ObjectHelper.newInstance(scriptClass, Script.class);
    }

    protected Binding createBinding(Exchange exchange, Map<String, Object> globalVariables) {
        return new ExchangeBinding(exchange, globalVariables);
    }

    private Resolved resolve(CamelContext context) {
        Resolved r = resolved;
        if (r == null || r.context != context) {
            GroovyLanguage language = (GroovyLanguage) context.resolveLanguage("groovy");
            Set<GroovyShellFactory> shellFactories = context.getRegistry().findByType(GroovyShellFactory.class);
            GroovyShellFactory shellFactory = shellFactories.size() == 1 ? shellFactories.iterator().next() : null;
            r = new Resolved(context, language, shellFactory);
            resolved = r;
        }
        return r;
    }

    private record Resolved(CamelContext context, GroovyLanguage language, GroovyShellFactory shellFactory) {
    }

    private record CompiledScript(
            CamelContext context, GroovyLanguage language, int generation, String fileName, Class<Script> scriptClass) {
    }

    /**
     * Binding with the same variables as {@link ExchangeHelper#populateVariableMap(Exchange, Map, boolean)} plus
     * attachments and log. All values are one snapshot taken when the binding is created; the values that are costly to
     * create (a copy of the exchange properties, the variable repository and the attachment message) are only created
     * when the script uses them, once per binding.
     */
    private static final class ExchangeBinding extends Binding {

        private final Exchange exchange;
        private final Message in;
        private final Object body;
        private final Map<String, Object> headers;
        private final Exception exception;
        private final Message out;
        private Map<String, Object> exchangeProperties;
        private Map<String, Object> exchangeVariables;
        private Map<?, ?> attachments;
        private boolean materialized;

        ExchangeBinding(Exchange exchange, Map<String, Object> globalVariables) {
            super(new HashMap<>());
            this.exchange = exchange;
            this.in = exchange.getIn();
            this.body = in.getBody();
            this.headers = in.getHeaders();
            this.exception = LanguageHelper.exception(exchange);
            this.out = ExchangeHelper.isOutCapable(exchange) ? exchange.getMessage() : null;
            if (!globalVariables.isEmpty()) {
                Map<String, Object> variables = super.getVariables();
                // the exchange variables take precedence over global variables with the same name
                globalVariables.forEach((k, v) -> {
                    if (!isExchangeVariable(k)) {
                        variables.put(k, v);
                    }
                });
            }
        }

        @Override
        public Object getVariable(String name) {
            Map<String, Object> variables = super.getVariables();
            if (materialized || variables.containsKey(name) || !isExchangeVariable(name)) {
                // throws MissingPropertyException when the variable does not exist
                return super.getVariable(name);
            }
            if (("out".equals(name) || "response".equals(name)) && out == null) {
                return super.getVariable(name);
            }
            return exchangeVariable(name);
        }

        @Override
        public boolean hasVariable(String name) {
            if (!materialized && isExchangeVariable(name)) {
                return out != null || !("out".equals(name) || "response".equals(name));
            }
            return super.hasVariable(name);
        }

        @Override
        public Map getVariables() {
            if (!materialized) {
                Map<String, Object> variables = super.getVariables();
                for (String name : new String[] {
                        "body", "header", "headers", "variable", "variables", "exception", "in", "request", "exchange",
                        "exchangeProperty", "exchangeProperties", "camelContext", "attachments", "log" }) {
                    // variables set by the script win
                    if (!variables.containsKey(name)) {
                        variables.put(name, exchangeVariable(name));
                    }
                }
                if (out != null) {
                    variables.putIfAbsent("out", out);
                    variables.putIfAbsent("response", out);
                }
                materialized = true;
            }
            return super.getVariables();
        }

        @Override
        public void removeVariable(String name) {
            getVariables();
            super.removeVariable(name);
        }

        /**
         * The value of a well known exchange variable, from the snapshot taken when the binding was created.
         */
        private Object exchangeVariable(String name) {
            switch (name) {
                case "body":
                    return body;
                case "header":
                case "headers":
                    return headers;
                case "variable":
                case "variables":
                    if (exchangeVariables == null) {
                        exchangeVariables = exchange.getVariables();
                    }
                    return exchangeVariables;
                case "exception":
                    return exception;
                case "in":
                case "request":
                    return in;
                case "exchange":
                    return exchange;
                case "exchangeProperty":
                case "exchangeProperties":
                    if (exchangeProperties == null) {
                        exchangeProperties = exchange.getAllProperties();
                    }
                    return exchangeProperties;
                case "out":
                case "response":
                    return out;
                case "camelContext":
                    return exchange.getContext();
                case "attachments":
                    if (attachments == null) {
                        AttachmentMessage am = new DefaultAttachmentMessage(exchange.getMessage());
                        attachments = am.hasAttachments() ? am.getAttachments() : Collections.EMPTY_MAP;
                    }
                    return attachments;
                case "log":
                    return LOG;
                default:
                    return null;
            }
        }

        private static boolean isExchangeVariable(String name) {
            switch (name) {
                case "body":
                case "header":
                case "headers":
                case "variable":
                case "variables":
                case "exception":
                case "in":
                case "request":
                case "exchange":
                case "exchangeProperty":
                case "exchangeProperties":
                case "out":
                case "response":
                case "camelContext":
                case "attachments":
                case "log":
                    return true;
                default:
                    return false;
            }
        }
    }
}
