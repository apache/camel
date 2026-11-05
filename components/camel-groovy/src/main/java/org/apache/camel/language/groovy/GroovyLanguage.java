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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import groovy.lang.Binding;
import groovy.lang.GString;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Ordered;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CompilePreProcessor;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.apache.camel.support.TypedLanguageSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Language("groovy")
@BodyTypes(expressionIn = @BodyType(value = Carrier.ANY, description = "The body as it is, in the variable body"),
           expressionOut = @BodyType(value = Carrier.UNKNOWN, javaTypeOption = "resultType",
                                     description = "What the script returns"))
public class GroovyLanguage extends TypedLanguageSupport implements ScriptingLanguage, Service {

    private static final Logger LOG = LoggerFactory.getLogger(GroovyLanguage.class);

    static final int RELOAD_ORDER = 100;

    /**
     * In case the expression is referring to an external resource, it indicates whether it is still needed to load the
     * resource.
     */
    private final boolean loadExternalResource;

    /**
     * Cache used to stores the compiled scripts (aka their classes)
     */
    private final Map<String, GroovyClassService> scriptCache;

    /**
     * Incremented whenever the script cache is cleared, so expressions holding a compiled script know it is stale.
     */
    private final AtomicInteger generation = new AtomicInteger();

    /**
     * The scripts being compiled, so concurrent cache misses of the same script compile it once.
     */
    private final ConcurrentMap<String, Object> compileLocks = new ConcurrentHashMap<>();

    private EventNotifier notifier;

    private GroovyLanguage(Map<String, GroovyClassService> scriptCache, boolean loadExternalResource) {
        this.scriptCache = scriptCache;
        this.loadExternalResource = loadExternalResource;
    }

    public GroovyLanguage() {
        // do not remove the class of an evicted script (stopOnEviction=false): a GroovyExpression may still hold and run
        // it. Classes are removed when the language stops or the cache is cleared on reload.
        this(LRUCacheFactory.newLRUSoftCache(16, 1000, false), true);
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
        generation.incrementAndGet();
        if (notifier != null) {
            getCamelContext().getManagementStrategy().removeEventNotifier(notifier);
            notifier = null;
        }
    }

    private final class ReloadNotifier extends SimpleEventNotifierSupport implements Ordered {

        @Override
        public void notify(CamelEvent event) throws Exception {
            // if context or route is reloading then clear cache to ensure old scripts are removed from memory.
            if (event instanceof CamelEvent.CamelContextReloadingEvent || event instanceof CamelEvent.RouteReloadedEvent) {
                ServiceHelper.stopService(scriptCache.values());
                scriptCache.clear();
                generation.incrementAndGet();
            }
        }

        @Override
        public int getOrder() {
            return RELOAD_ORDER;
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
        try (GroovyLanguage factory = new GroovyLanguage()) {
            return factory.createExpression(expression);
        } catch (IOException e) {
            LOG.error("Some error happened while creating a Groovy expression", e);
            return null;
        }
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
        final String text = script;
        Class<Script> clazz = getOrCompile(text, () -> {
            // prefer to use classloader from groovy script compiler, and if not fallback to app context
            ClassLoader cl = getCamelContext().getCamelContextExtension().getContextPlugin(GroovyScriptClassLoader.class);
            GroovyShell shell = cl != null ? new GroovyShell(cl) : new GroovyShell();
            preCompile(getCamelContext(), null, text);
            try {
                return shell.getClassLoader().parseClass(text);
            } catch (CompilationFailedException e) {
                throw compileFailure(e, shell.getClassLoader());
            }
        });
        Script gs = ObjectHelper.newInstance(clazz, Script.class);
        if (bindings != null) {
            gs.setBinding(new Binding(bindings));
        }
        gs.getBinding().setVariable("log", LOG);
        Object value = scriptResult(gs.run());
        return getCamelContext().getTypeConverter().convertTo(resultType, value);
    }

    // use by tooling
    public boolean validateExpression(String expression) throws GroovyValidationException {
        final Exchange dummy = new DefaultExchange(getCamelContext());
        Map<String, Object> globalVariables = new HashMap<>();

        try {
            GroovyExpression ge = createExpression(expression);
            Script script = ge.instantiateScript(dummy, globalVariables);
            script.setBinding(ge.createBinding(dummy, globalVariables));
            script.run();
        } catch (Exception e) {
            throw new GroovyValidationException(expression, e);
        }
        return true;
    }

    // use by tooling
    public boolean validatePredicate(String expression) throws GroovyValidationException {
        return validateExpression(expression);
    }

    int getGeneration() {
        return generation.get();
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

    /**
     * Gets the compiled class of the script from the cache, compiling and caching it on a miss. Concurrent misses of
     * the same key compile the script once: the callers wait for the compilation in flight and then find it in the
     * cache. The cache hit path does not take a lock.
     */
    Class<Script> getOrCompile(String key, Supplier<Class<Script>> compiler) {
        Class<Script> clazz = getScriptFromCache(key);
        if (clazz != null) {
            return clazz;
        }
        Object lock = compileLocks.computeIfAbsent(key, k -> new Object());
        try {
            synchronized (lock) {
                clazz = getScriptFromCache(key);
                if (clazz == null) {
                    clazz = compiler.get();
                    addScriptToCache(key, clazz);
                }
                return clazz;
            }
        } finally {
            compileLocks.remove(key, lock);
        }
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

    /**
     * The value that a script returns, with a GString ({@code "Hello ${name}"}) as a String, as the other script
     * languages return their text. A GString is not equal to a String, and components that check for a String do not
     * take it as text: camel-jms sends it as an ObjectMessage.
     */
    static Object scriptResult(Object value) {
        return value instanceof GString ? value.toString() : value;
    }

    /**
     * Runs the registered {@link CompilePreProcessor}s on the script before it is compiled, as the Java DSL does for
     * its sources: with the Camel CLI that downloads the known library of an import (CAMEL-24843).
     */
    static void preCompile(CamelContext context, String name, String code) {
        for (CompilePreProcessor pre : context.getRegistry().findByType(CompilePreProcessor.class)) {
            try {
                pre.preCompile(context, name, code);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }
    }

    /**
     * The packages of the classes a script reaches for that can need an import: JsonSlurper is a class of groovy.json.
     * A class Groovy finds by itself (java.lang, java.util, java.io, java.net, groovy.lang, groovy.util) compiles, so a
     * package here that a Groovy version imports is never asked about.
     */
    private static final List<String> NOT_IMPORTED = List.of("groovy.json", "groovy.xml", "groovy.time",
            "groovy.transform", "java.time", "java.time.format", "java.time.temporal", "java.util.concurrent",
            "java.util.concurrent.atomic", "java.util.function", "java.util.stream", "java.util.regex", "java.nio.file",
            "java.nio.charset", "java.text", "java.math", "java.security", "java.sql", "org.apache.camel");

    /**
     * The classes of the simple name in the packages a script does not import by default, that the class loader has:
     * groovy.json.JsonSlurper for JsonSlurper. Empty when the name is not a class there.
     */
    static List<String> notImported(String simpleName, ClassLoader classLoader) {
        List<String> answer = new ArrayList<>();
        if (simpleName == null || simpleName.isEmpty() || !Character.isUpperCase(simpleName.charAt(0))) {
            return answer;
        }
        for (String pkg : NOT_IMPORTED) {
            try {
                Class.forName(pkg + "." + simpleName, false, classLoader);
                answer.add(pkg + "." + simpleName);
            } catch (ClassNotFoundException | LinkageError e) {
                // not a class of this package
            }
        }
        return answer;
    }

    /**
     * A compilation failure whose cause is a class the script imports and the classpath does not have: the compiler
     * names the class and nothing else; the message adds what to do, without knowing the runtime (CAMEL-24843).
     */
    static RuntimeException compileFailure(CompilationFailedException e) {
        return compileFailure(e, GroovyLanguage.class.getClassLoader());
    }

    /**
     * As {@link #compileFailure(CompilationFailedException)}, with the class loader the script was compiled with: a
     * class it has is not missing, it needs an import. r27 of the local-model benchmark wrote new JsonSlurper() with no
     * import, was told the class is not on the classpath, added a dependency for it, and read the log 17 times.
     */
    static RuntimeException compileFailure(CompilationFailedException e, ClassLoader classLoader) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("unable to resolve class")) {
            String cls = msg.substring(msg.indexOf("unable to resolve class") + "unable to resolve class".length()).trim();
            int end = cls.indexOf('\n');
            cls = (end > 0 ? cls.substring(0, end) : cls).trim();
            String simple = cls.substring(cls.lastIndexOf('.') + 1);
            List<String> classes = notImported(simple, classLoader);
            classes.remove(cls);
            if (!classes.isEmpty()) {
                String fqn = classes.get(0);
                String others = classes.size() > 1 ? " (or " + String.join(", ", classes.subList(1, classes.size())) + ")" : "";
                if (cls.contains(".")) {
                    // import groovy.util.JsonSlurper: the class of that name is in another package
                    return new RuntimeCamelException(
                            "Groovy cannot resolve the class " + cls + ": the class " + simple + " is " + fqn + others
                                                     + ". Import it as import " + fqn + " at the top of the script.",
                            e);
                }
                // the source line of the error shows how the class is used
                String created = msg.contains("new " + simple + "()") ? "new " + fqn + "()"
                        : msg.contains("new " + simple + "(") ? "new " + fqn + "(...)" : fqn;
                return new RuntimeCamelException(
                        "Groovy cannot resolve the class " + simple + ": it is " + fqn + others + ", which the script"
                                                 + " does not import. Add import " + fqn + " at the top of the script,"
                                                 + " or write " + created + ".",
                        e);
            }
            return new RuntimeCamelException(
                    "Groovy cannot resolve the class " + cls + ": it is not on the classpath. Add the library that"
                                             + " provides it as a dependency of the application: a Maven dependency,"
                                             + " or with the Camel CLI"
                                             + " camel.jbang.dependencies=groupId:artifactId:version in"
                                             + " application.properties. A class of your own goes in a .groovy or"
                                             + " .java file next to the route.",
                    e);
        }
        return e;
    }
}
