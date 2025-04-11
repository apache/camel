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

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.apache.camel.Service;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.apache.camel.support.TypedLanguageSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.codehaus.groovy.runtime.InvokerHelper;

@Language("groovy")
public class GroovyLanguage extends TypedLanguageSupport implements ScriptingLanguage, Service {

    /**
     * In case the expression is referring to an external resource, it indicates whether it is still needed to load the
     * resource.
     */
    private final boolean loadExternalResource;

    /**
     * Cache used to stores the compiled scripts (aka their classes)
     */
    private final Map<String, GroovyClassService> scriptCache;

    private EventNotifier notifier;

    private GroovyLanguage(Map<String, GroovyClassService> scriptCache, boolean loadExternalResource) {
        this.scriptCache = scriptCache;
        this.loadExternalResource = loadExternalResource;
    }

    public GroovyLanguage() {
        this(LRUCacheFactory.newLRUSoftCache(16, 1000, true), true);
    }

    @Override
    public void start() {
        // are we in dev mode then support flushing cache on reload
        if (getCamelContext() != null) {
            String profile = getCamelContext().getCamelContextExtension().getProfile();
            if ("dev".equals(profile)) {
                if (notifier == null) {
                    notifier = new ReloadNotifier();
                    getCamelContext().getManagementStrategy().addEventNotifier(notifier);
                }
            }
        }
    }

    @Override
    public void stop() {
        ServiceHelper.stopService(scriptCache.values());
        scriptCache.clear();
        if (notifier != null) {
            getCamelContext().getManagementStrategy().removeEventNotifier(notifier);
            notifier = null;
        }
    }

    private final class ReloadNotifier extends SimpleEventNotifierSupport {

        @Override
        public void notify(CamelEvent event) throws Exception {
            // if context or route is reloading then clear cache to ensure old scripts are removed from memory.
            if (event instanceof CamelEvent.CamelContextReloadingEvent || event instanceof CamelEvent.RouteReloadedEvent) {
                ServiceHelper.stopService(scriptCache.values());
                scriptCache.clear();
            }
        }
    }

    private static final class GroovyClassService implements Service {

        private final Class<Script> script;

        private GroovyClassService(Class<Script> script) {
            this.script = script;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
            InvokerHelper.removeClass(script);
        }

    }

    public static GroovyExpression groovy(String expression) {
        return new GroovyLanguage().createExpression(expression);
    }

    @Override
    public GroovyExpression createPredicate(String expression) {
        return createExpression(expression);
    }

    @Override
    public GroovyExpression createExpression(String expression) {
        expression = loadResource(expression);
        return new GroovyExpression(expression);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T evaluate(String script, Map<String, Object> bindings, Class<T> resultType) {
        if (loadExternalResource) {
            script = loadResource(script);
        }
        Class<Script> clazz = getScriptFromCache(script);
        if (clazz == null) {
            ClassLoader cl = getCamelContext().getApplicationContextClassLoader();
            GroovyShell shell = new GroovyShell(cl);
            clazz = shell.getClassLoader().parseClass(script);
            addScriptToCache(script, clazz);
        }
        Script gs = ObjectHelper.newInstance(clazz, Script.class);

        if (bindings != null) {
            gs.setBinding(new Binding(bindings));
        }
        Object value = gs.run();
        return getCamelContext().getTypeConverter().convertTo(resultType, value);
    }

    Class<Script> getScriptFromCache(String script) {
        final GroovyClassService cached = scriptCache.get(script);
        if (cached == null) {
            return null;
        }
        return cached.script;
    }

    void addScriptToCache(String script, Class<Script> scriptClass) {
        scriptCache.put(script, new GroovyClassService(scriptClass));
    }

    public static class Builder {
        private final Map<String, GroovyClassService> cache = new HashMap<>();

        public void addScript(String content, Class<Script> scriptClass) {
            cache.put(content, new GroovyClassService(scriptClass));
        }

        public GroovyLanguage build() {
            return new GroovyLanguage(cache, false);
        }
    }
}
