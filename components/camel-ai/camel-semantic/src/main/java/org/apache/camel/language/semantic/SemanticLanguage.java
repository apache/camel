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
package org.apache.camel.language.semantic;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.semantic.SemanticAdapter;
import org.apache.camel.semantic.SemanticQuestion;
import org.apache.camel.semantic.SemanticQuestions;
import org.apache.camel.semantic.SemanticResult;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;

/** Evaluates a named, provider-independent question against selected message state. */
@Language(value = "semantic", modelName = "language")
@Metadata(title = "Semantic Evaluation",
          description = "Evaluate named questions about message content to produce boolean decisions, categories and scores through provider adapters",
          label = "language,ai", firstVersion = "4.23.0")
public class SemanticLanguage extends LanguageSupport {
    public static final String RESULT = "CamelSemanticResult";
    public static final String ADAPTER_NAME = "camelSemanticAdapter";
    public static final String ADAPTER_FACTORY = "semantic-adapter";
    public static final String ADAPTER_RESOURCE = FactoryFinder.DEFAULT_PATH + ADAPTER_FACTORY;

    private String adapter;
    private String defaultState = "${body}";
    private SemanticAdapter selectedAdapter;

    public String getAdapter() {
        return adapter;
    }

    /**
     * Adapter registry bean name or fully qualified implementation class name, without a reference prefix. Registry
     * lookup takes precedence over class resolution. Absent selects the sole advertised adapter.
     */
    public void setAdapter(String adapter) {
        this.adapter = adapter;
    }

    public String getDefaultState() {
        return defaultState;
    }

    /** Default Simple state selector for questions without their own selector. Defaults to the body. */
    public void setDefaultState(String defaultState) {
        this.defaultState = defaultState;
    }

    @Override
    public Expression createExpression(String expression) {
        return createEvaluation(expression, false);
    }

    @Override
    public Predicate createPredicate(String expression) {
        return createEvaluation(expression, true);
    }

    public boolean validateExpression(String expression) {
        if (expression == null || !expression.startsWith("ref:") || expression.substring(4).isBlank()) {
            throw new IllegalArgumentException("Semantic expression must reference a named question using ref:name");
        }
        return true;
    }

    public boolean validatePredicate(String expression) {
        return validateExpression(expression);
    }

    private Evaluation createEvaluation(String expression, boolean predicate) {
        validateExpression(expression);
        Evaluation evaluation = new Evaluation(expression.substring(4), predicate);
        if (getCamelContext() != null) {
            evaluation.init(getCamelContext());
        }
        return evaluation;
    }

    private synchronized SemanticAdapter adapter() {
        if (selectedAdapter != null) {
            return selectedAdapter;
        }
        CamelContext context = getCamelContext();
        String configured = adapter == null ? null : context.resolvePropertyPlaceholders(adapter);
        if (configured != null) {
            if (configured.isBlank() || configured.startsWith("#")) {
                throw new IllegalArgumentException("Semantic adapter must be a bean name or class name without a # prefix");
            }
            Object bean = context.getRegistry().lookupByName(configured);
            if (bean != null) {
                if (!(bean instanceof SemanticAdapter found)) {
                    throw new IllegalArgumentException(
                            "Semantic adapter bean does not implement SemanticAdapter: " + configured);
                }
                selectedAdapter = found;
                return found;
            }
        }
        ManagedAdapter owned = null;
        try {
            Class<?> resolved = configured == null
                    ? discoverAdapter(context) : context.getClassResolver().resolveClass(configured);
            if (resolved == null) {
                throw new IllegalArgumentException("No semantic adapter bean or class found: " + configured);
            }
            Class<? extends SemanticAdapter> type = resolved.asSubclass(SemanticAdapter.class);
            AdapterLock lock = AdapterLock.get(context);
            synchronized (lock.monitor) {
                if (context.getRegistry().lookupByName(ADAPTER_NAME) != null) {
                    throw new IllegalArgumentException("Semantic adapter registry name is already bound: " + ADAPTER_NAME);
                }
                SemanticAdapter instance = context.getInjector().newInstance(type);
                CamelContextAware.trySetCamelContext(instance, context);
                owned = new ManagedAdapter(context, instance, lock);
                context.getRegistry().bind(ADAPTER_NAME, instance);
            }
            context.addService(owned, true, true);
            selectedAdapter = owned.instance;
            return selectedAdapter;
        } catch (Exception failure) {
            if (owned != null) {
                try {
                    context.removeService(owned);
                    ServiceHelper.stopAndShutdownService(owned);
                } catch (Exception cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            throw RuntimeCamelException.wrapRuntimeCamelException(failure);
        }
    }

    private Class<?> discoverAdapter(CamelContext context) throws IOException {
        // FactoryFinder resolves one descriptor; check all declarations first to avoid classpath-order selection.
        Set<String> candidates = new TreeSet<>();
        Enumeration<URL> resources = context.getClassResolver().loadAllResourcesAsURL(ADAPTER_RESOURCE);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            try (InputStream input = resource.openStream()) {
                Properties properties = new Properties();
                properties.load(IOHelper.buffered(input));
                String className = properties.getProperty("class");
                if (className == null || className.isBlank()) {
                    throw new IllegalArgumentException("Semantic adapter descriptor requires a class property: " + resource);
                }
                candidates.add(className);
            }
        }
        if (candidates.size() != 1) {
            throw new IllegalArgumentException(
                    "Semantic language requires exactly one advertised adapter; found "
                                               + candidates + ". Configure camel.language.semantic.adapter explicitly");
        }
        Class<?> resolved = context.getCamelContextExtension().getDefaultFactoryFinder().findClass(ADAPTER_FACTORY)
                .orElseThrow(() -> new IllegalArgumentException("Cannot resolve advertised semantic adapter: " + candidates));
        if (!candidates.contains(resolved.getName())) {
            throw new IllegalArgumentException(
                    "Resolved semantic adapter " + resolved.getName() + " does not match advertised adapter: " + candidates);
        }
        return resolved;
    }

    private static final class AdapterLock {
        private static final Object CREATION_LOCK = new Object();
        private final Object monitor = new Object();

        private static AdapterLock get(CamelContext context) {
            synchronized (CREATION_LOCK) {
                AdapterLock lock = context.getCamelContextExtension().getContextPlugin(AdapterLock.class);
                if (lock == null) {
                    lock = new AdapterLock();
                    context.getCamelContextExtension().addContextPlugin(AdapterLock.class, lock);
                }
                return lock;
            }
        }
    }

    private static final class ManagedAdapter extends ServiceSupport {
        private final CamelContext context;
        private final SemanticAdapter instance;
        private final AdapterLock lock;

        private ManagedAdapter(CamelContext context, SemanticAdapter instance, AdapterLock lock) {
            this.context = context;
            this.instance = instance;
            this.lock = lock;
        }

        @Override
        protected void doInit() throws Exception {
            ServiceHelper.initService(instance);
        }

        @Override
        protected void doStart() throws Exception {
            ServiceHelper.startService(instance);
        }

        @Override
        protected void doStop() throws Exception {
            ServiceHelper.stopService(instance);
        }

        @Override
        protected void doShutdown() throws Exception {
            try {
                ServiceHelper.stopAndShutdownService(instance);
            } finally {
                synchronized (lock.monitor) {
                    if (context.getRegistry().lookupByName(ADAPTER_NAME) == instance) {
                        context.getRegistry().unbind(ADAPTER_NAME);
                    }
                }
            }
        }
    }

    private final class Evaluation extends ExpressionAdapter {
        private final String name;
        private final boolean predicate;
        private volatile Compiled compiled;
        private volatile SemanticQuestions questions;
        private volatile SemanticAdapter provider;

        private Evaluation(String name, boolean predicate) {
            this.name = name;
            this.predicate = predicate;
        }

        @Override
        public void init(CamelContext context) {
            super.init(context);
            questions = SemanticQuestions.get(context);
            SemanticQuestion question = questions.get(name);
            if (predicate && question.getType() != SemanticQuestion.Type.BOOLEAN) {
                throw new IllegalArgumentException("Semantic predicate requires a boolean question: " + name);
            }
            provider = adapter();
            compile(question);
        }

        private synchronized Compiled compile(SemanticQuestion question) {
            if (compiled == null || compiled.question != question) {
                if (predicate && question.getType() != SemanticQuestion.Type.BOOLEAN) {
                    throw new IllegalArgumentException("Semantic predicate requires a boolean question: " + name);
                }
                provider.validate(question);
                String selector = question.getState() != null ? question.getState() : defaultState;
                if (selector == null || selector.isBlank()) {
                    throw new IllegalArgumentException("Semantic state selector must not be blank for question: " + name);
                }
                selector = getCamelContext().resolvePropertyPlaceholders(selector);
                Expression state = getCamelContext().resolveLanguage("simple").createExpression(selector);
                state.init(getCamelContext());
                compiled = new Compiled(question, state);
            }
            return compiled;
        }

        @Override
        public Object evaluate(Exchange exchange) {
            exchange.removeProperty(RESULT);
            try {
                SemanticQuestion question = questions.get(name);
                Compiled current = compiled;
                if (current == null || current.question != question) {
                    current = compile(question);
                }
                Object state = current.state.evaluate(exchange, Object.class);
                if (state == null) {
                    throw new IllegalArgumentException("Missing selected state for semantic question: " + name);
                }
                if (!(state instanceof String || state instanceof Map<?, ?> || state instanceof List<?>)) {
                    throw new IllegalArgumentException(
                            "Unsupported state type for semantic question: " + name
                                                       + ". Select strings, maps or lists explicitly");
                }
                SemanticResult result = provider.evaluate(question, state);
                if (result == null) {
                    throw new IllegalArgumentException("Missing result for semantic question: " + name);
                }
                Object decision = result.decision(question);
                exchange.setProperty(RESULT, result);
                return decision;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        @Override
        public boolean matches(Exchange exchange) {
            exchange.removeProperty(RESULT);
            if (questions.get(name).getType() != SemanticQuestion.Type.BOOLEAN) {
                throw new IllegalArgumentException("Semantic predicate requires a boolean question: " + name);
            }
            return (Boolean) evaluate(exchange);
        }

        @Override
        public String toString() {
            return "semantic[ref:" + name + "]";
        }
    }

    private record Compiled(SemanticQuestion question, Expression state) {
    }
}
