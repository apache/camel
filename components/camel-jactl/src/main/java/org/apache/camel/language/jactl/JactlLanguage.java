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
package org.apache.camel.language.jactl;

import java.util.HashMap;
import java.util.Map;

import io.jactl.Jactl;
import io.jactl.JactlContext;
import io.jactl.JactlScript;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.TypedLanguageSupport;

/**
 * Camel expression language for <a href="https://jactl.io">Jactl</a>.
 *
 * <p>
 * Scripts see these variables:
 * </p>
 * <ul>
 * <li>{@code body}</li>
 * <li>{@code header} / {@code headers}</li>
 * <li>{@code variable} / {@code variables}</li>
 * </ul>
 *
 * <p>
 * If {@code withAllowContextMapAll(true)} is configured, these variables are also available:
 * </p>
 * <ul>
 * <li>{@code request}</li>
 * <li>{@code response}</li>
 * <li>{@code exception}</li>
 * <li>{@code exchange}</li>
 * <li>{@code exchangeProperty} / {@code exchangeProperties}</li>
 * <li>{@code camelContext}</li>
 * </ul>
 * <p>
 * NOTE: The types of the {@code request}, {@code response}, {@code exception}, {@code exchange}, and
 * {@code camelContext} variables are not standard supported types in Jactl so if you need to invoke methods on these
 * objects you will need to either use {@code withHostAccess()} which completely disables the sandbox security and
 * allows Jactl code to invoke methods on any objects, or use {@code withHostAccess(Predicate<String>)} to selectively
 * configure which additional classes you will allow Jactl code to access.
 * </p>
 *
 * <p>
 * To configure {@code withAllowContextMapAll()} or {@code withHostAccess()} you should bind the "jactl" language to the
 * configured {@code JactlLanguage} instance before the first usage. For example:
 * </p>
 *
 * <pre>
 * JactlLanguage jactl = JactlLanguage.withHostAccess(name -> name.startsWith("com.acme."))
 *         .withAllowContextMapAll();
 * camelContext.getRegistry().bind("jactl", jactl);
 * </pre>
 *
 * <p>
 * Scripts are compiled once and cached by script text in an LRU cache that defaults to 1000 entries. You can override
 * the size by setting the {@code camel-jactl.maxCacheSize} system property.
 * </p>
 */
@Language("jactl")
public class JactlLanguage extends TypedLanguageSupport implements ScriptingLanguage {

    // Create a globals variable for use during compilation.
    // Values for each key determine the type that the expressions will expect
    // runtime.
    private static final Map<String, Object> DECLARED_GLOBALS = new HashMap<>() {
        {
            put("body", Jactl.type(Object.class));
            put("header", Jactl.type(Map.class));
            put("headers", Jactl.type(Map.class));
            put("variable", Jactl.type(Map.class));
            put("variables", Jactl.type(Map.class));
        }
    };

    private static final Map<String, Object> DECLARED_GLOBALS_ALL = new HashMap<>() {
        {
            put("body", Jactl.type(Object.class));
            put("header", Jactl.type(Map.class));
            put("headers", Jactl.type(Map.class));
            put("variable", Jactl.type(Map.class));
            put("variables", Jactl.type(Map.class));
            put("exception", Jactl.type(Exception.class));
            put("request", Jactl.type(Message.class));
            put("response", Jactl.type(Message.class));
            put("exchange", Jactl.type(Exchange.class));
            put("exchangeProperty", Jactl.type(Map.class));
            put("exchangeProperties", Jactl.type(Map.class));
            put("camelContext", Jactl.type(CamelContext.class));
        }
    };

    private final JactlContext jactlContext;
    boolean allowContextMapAll = false;

    // Cache of compiled scripts
    private final Map<String, JactlScript> scriptCache
            = LRUCacheFactory.newLRUSoftCache(16, Integer.getInteger("camel-jactl.maxCacheSize", 1000));

    public JactlLanguage() {
        this(JactlContext.create().async(false).build());
    }

    public JactlLanguage(JactlContext jactlContext) {
        this.jactlContext = jactlContext;
    }

    public static JactlLanguage create() {
        return new JactlLanguage();
    }

    /**
     * Creates a language with the Jactl sandbox security disabled
     */
    public static JactlLanguage createWithHostAccess() {
        return createWithHostAccess(name -> true);
    }

    /**
     * Creates a language allowing method calls only on host objects whose class is accepted by the given predicate
     * (maps to {@code JactlContext.allowHostClassLookup}). The predicate is passed the full class name of the runtime
     * type of the object on which a method invocation is being attempted.
     */
    public static JactlLanguage createWithHostAccess(java.util.function.Predicate<String> allowedClasses) {
        return new JactlLanguage(
                JactlContext.create()
                        .async(false)
                        .allowHostAccess(true)
                        .allowHostClassLookup(allowedClasses)
                        .build());
    }

    public JactlLanguage withAllowContextMapAll(boolean allowContextMapAll) {
        this.allowContextMapAll = allowContextMapAll;
        return this;
    }

    /**
     * Helper for use in the Java route DSL, e.g. {@code .filter(JactlLanguage.jactl("body.age > 20"))}. The script is
     * compiled on first evaluation using the "jactl" language registered with the CamelContext.
     */
    public static JactlExpression jactl(String script) {
        return new JactlExpression(script, null);
    }

    @Override
    public Predicate createPredicate(String expression) {
        return _create(expression);
    }

    @Override
    public Expression createExpression(String expression) {
        return _create(expression);
    }

    private JactlExpression _create(String expression) {
        return new JactlExpression(expression, getScript(expression), allowContextMapAll);
    }

    JactlScript getScript(String script) {
        JactlScript compiled = scriptCache.get(script);
        if (compiled == null) {
            // Concurrent first use of the same script may compile twice
            compiled = Jactl.compileScript(script,
                    allowContextMapAll ? DECLARED_GLOBALS_ALL : DECLARED_GLOBALS,
                    jactlContext);
            scriptCache.put(script, compiled);
        }
        return compiled;
    }

    /**
     * Evaluates a script with the given bindings (no Exchange involved). Compiles each time so it should not be used in
     * scenarios where the same script needs to be evaluated many times.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T evaluate(String script, Map<String, Object> bindings, Class<T> resultType) {
        Map<String, Object> globals = bindings == null ? new HashMap<>() : new HashMap<>(bindings);
        Map<String, Object> declared = new HashMap<>();
        globals.keySet().forEach(name -> declared.put(name, null));
        JactlScript compiled = Jactl.compileScript(script, declared, jactlContext);
        Object result = compiled.eval(globals);
        if (resultType == null || Object.class.equals(resultType)) {
            return (T) result;
        }
        if (getCamelContext() != null) {
            return getCamelContext().getTypeConverter().convertTo(resultType, result);
        }
        return resultType.cast(result);
    }
}
