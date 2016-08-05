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
package org.apache.camel.builder.script;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A builder class for creating {@link Processor}, {@link Expression} and
 * {@link Predicate} objects using the JSR 223 scripting engine.
 *
 * @version
 */
public class ScriptBuilder implements Expression, Predicate, Processor {

    /**
     * Additional arguments to {@link ScriptEngine} provided as a header on the IN {@link org.apache.camel.Message}
     * using the key {@link #ARGUMENTS}
     */
    public static final String ARGUMENTS = "CamelScriptArguments";

    private static final Logger LOG = LoggerFactory.getLogger(ScriptBuilder.class);

    private Map<String, Object> attributes;
    private final CamelContext camelContext;
    private final ScriptEngineFactory scriptEngineFactory;
    private final ScriptEngine scriptEngine;
    private final String scriptLanguage;
    private final String scriptResource;
    private final String scriptText;
    private CompiledScript compiledScript;

    /**
     * Constructor.
     *
     * @param scriptLanguage the name of the scripting language
     * @param scriptText the script text to be evaluated, or a reference to a script resource
     */
    public ScriptBuilder(String scriptLanguage, String scriptText) {
        this(null, scriptLanguage, scriptText, null);
    }

    /**
     * Constructor.
     *
     * @param scriptLanguage the name of the scripting language
     * @param scriptText the script text to be evaluated, or a reference to a script resource
     */
    public ScriptBuilder(CamelContext camelContext, String scriptLanguage, String scriptText) {
        this(camelContext, scriptLanguage, scriptText, null);
    }

    /**
     * Constructor.
     *
     * @param scriptLanguage the name of the scripting language
     * @param scriptText the script text to be evaluated, or a reference to a script resource
     * @param scriptEngineFactory the script engine factory
     */
    public ScriptBuilder(CamelContext camelContext, String scriptLanguage, String scriptText, ScriptEngineFactory scriptEngineFactory) {
        this.camelContext = camelContext;
        this.scriptLanguage = scriptLanguage;
        if (ResourceHelper.hasScheme(scriptText)) {
            this.scriptResource = scriptText;
            this.scriptText = null;
        } else {
            this.scriptResource = null;
            this.scriptText = scriptText;
        }
        if (scriptEngineFactory == null) {
            this.scriptEngine = createScriptEngine(scriptLanguage, false);
            this.scriptEngineFactory = lookupScriptEngineFactory(scriptLanguage);
        } else {
            this.scriptEngineFactory = scriptEngineFactory;
            this.scriptEngine = scriptEngineFactory.getScriptEngine();
        }

        if (this.scriptEngineFactory == null) {
            throw new IllegalArgumentException("Cannot lookup ScriptEngineFactory for script language: " + scriptLanguage);
        }

        // bean shell is not compileable
        if (isBeanShell(scriptLanguage)) {
            return;
        }

        // pre-compile script which would execute faster if possible
        Reader reader = null;
        try {
            // if we have camel context then load resources
            if (camelContext != null && scriptResource != null) {
                reader = createScriptReader(camelContext.getClassResolver(), scriptResource);
            } else if (this.scriptText != null) {
                reader = new StringReader(this.scriptText);
            }

            // pre-compile script if we have it as text
            if (reader != null) {
                if (compileScripte(camelContext) && scriptEngine instanceof Compilable) {
                    Compilable compilable = (Compilable) scriptEngine;
                    this.compiledScript = compilable.compile(reader);
                    LOG.debug("Using compiled script: {}", this.compiledScript);
                }
            }
        } catch (IOException e) {
            throw new ScriptEvaluationException("Cannot load " + scriptLanguage + " script from resource: " + scriptResource, e);
        } catch (ScriptException e) {
            throw new ScriptEvaluationException("Error compiling " + scriptLanguage + " script: " + scriptText, e);
        } finally {
            IOHelper.close(reader);
        }
    }

    @Override
    public String toString() {
        return getScriptDescription();
    }

    public Object evaluate(Exchange exchange) {
        return evaluateScript(exchange);
    }

    public <T> T evaluate(Exchange exchange, Class<T> type) {
        Object result = evaluate(exchange);
        return exchange.getContext().getTypeConverter().convertTo(type, result);
    }

    public boolean matches(Exchange exchange) {
        Object scriptValue = evaluateScript(exchange);
        return matches(exchange, scriptValue);
    }

    public void assertMatches(String text, Exchange exchange) throws AssertionError {
        Object scriptValue = evaluateScript(exchange);
        if (!matches(exchange, scriptValue)) {
            throw new AssertionError(this + " failed on " + exchange + " as script returned <" + scriptValue + ">");
        }
    }

    public void process(Exchange exchange) {
        evaluateScript(exchange);
    }

    // Builder API
    // -------------------------------------------------------------------------

    /**
     * Sets the attribute on the context so that it is available to the script
     * as a variable in the {@link ScriptContext#ENGINE_SCOPE}
     *
     * @param name the name of the attribute
     * @param value the attribute value
     * @return this builder
     */
    public ScriptBuilder attribute(String name, Object value) {
        if (attributes == null) {
            attributes = new HashMap<String, Object>();
        }
        attributes.put(name, value);
        return this;
    }

    /**
     * Creates a script builder for the named language and script contents
     *
     * @param language the language to use for the script
     * @param scriptText the script text to be evaluated, or a reference to a script resource
     * @return the builder
     */
    public static ScriptBuilder script(String language, String scriptText) {
        return new ScriptBuilder(language, scriptText);
    }

    /**
     * Creates a script builder for the groovy script contents
     *
     * @param scriptText the script text to be evaluated, or a reference to a script resource
     * @return the builder
     */
    public static ScriptBuilder groovy(String scriptText) {
        return new ScriptBuilder("groovy", scriptText);
    }

    /**
     * Creates a script builder for the JavaScript/ECMAScript script contents
     *
     * @param scriptText the script text to be evaluated, or a reference to a script resource
     * @return the builder
     */
    public static ScriptBuilder javaScript(String scriptText) {
        return new ScriptBuilder("js", scriptText);
    }

    /**
     * Creates a script builder for the PHP script contents
     *
     * @param scriptText the script text to be evaluated, or a reference to a script resource
     * @return the builder
     */
    public static ScriptBuilder php(String scriptText) {
        return new ScriptBuilder("php", scriptText);
    }

    /**
     * Creates a script builder for the Python script contents
     *
     * @param scriptText the script text to be evaluated, or a reference to a script resource
     * @return the builder
     */
    public static ScriptBuilder python(String scriptText) {
        return new ScriptBuilder("python", scriptText);
    }

    /**
     * Creates a script builder for the Ruby/JRuby script contents
     *
     * @param scriptText the script text to be evaluated, or a reference to a script resource
     * @return the builder
     */
    public static ScriptBuilder ruby(String scriptText) {
        return new ScriptBuilder("jruby", scriptText);
    }

    /**
     * Whether the given language is a language that is supported by a scripting engine.
     */
    public static boolean supportScriptLanguage(String language) {
        return createScriptEngine(language, true) != null;
    }

    // Properties
    // -------------------------------------------------------------------------

    public CompiledScript getCompiledScript() {
        return compiledScript;
    }

    public String getScriptLanguage() {
        return scriptLanguage;
    }

    /**
     * Returns a description of the script
     *
     * @return the script description
     */
    public String getScriptDescription() {
        if (scriptText != null) {
            return scriptLanguage + ": " + scriptText;
        } else if (scriptResource != null) {
            return scriptLanguage + ": " + scriptResource;
        } else {
            return scriptLanguage + ": null script";
        }
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    protected boolean matches(Exchange exchange, Object scriptValue) {
        return exchange.getContext().getTypeConverter().convertTo(boolean.class, scriptValue);
    }

    private static String[] getScriptNames(String name) {
        if (name.equals("js")) {
            return new String[]{"js", "javaScript", "ECMAScript"};
        } else if (name.equals("javaScript")) {
            return new String[]{"javaScript", "js", "ECMAScript"};
        } else if (name.equals("ECMAScript")) {
            return new String[]{"ECMAScript", "javaScript", "js"};
        }
        return new String[]{name};
    }

    protected static ScriptEngineFactory lookupScriptEngineFactory(String language) {
        ScriptEngineManager manager = new ScriptEngineManager();
        for (ScriptEngineFactory factory : manager.getEngineFactories()) {
            // some script names has alias
            String[] names = getScriptNames(language);
            for (String name : names) {
                if (factory.getLanguageName().equals(name)) {
                    return factory;
                }
            }
        }

        // fallback to get engine by name
        ScriptEngine engine = createScriptEngine(language, true);
        if (engine != null) {
            return engine.getFactory();
        }
        return null;
    }

    protected static ScriptEngine createScriptEngine(String language, boolean allowNull) {
        ScriptEngine engine = tryCreateScriptEngine(language, ScriptBuilder.class.getClassLoader());
        if (engine == null) {
            engine = tryCreateScriptEngine(language, Thread.currentThread().getContextClassLoader());
        }
        if (engine == null && !allowNull) {
            throw new IllegalArgumentException("No script engine could be created for: " + language);
        }
        return engine;
    }

    /**
     * Attemps to create the script engine for the given langauge using the classloader
     *
     * @return the engine, or <tt>null</tt> if not able to create
     */
    private static ScriptEngine tryCreateScriptEngine(String language, ClassLoader classLoader) {
        ScriptEngineManager manager = new ScriptEngineManager(classLoader);
        ScriptEngine engine = null;

        // some script names has alias
        String[] names = getScriptNames(language);
        for (String name : names) {
            try {
                engine = manager.getEngineByName(name);
                if (engine != null) {
                    break;
                }
            } catch (NoClassDefFoundError ex) {
                LOG.warn("Cannot load ScriptEngine for " + name + ". Please ensure correct JARs is provided on classpath (stacktrace in DEBUG logging).");
                // include stacktrace in debug logging
                LOG.debug("Cannot load ScriptEngine for " + name + ". Please ensure correct JARs is provided on classpath.", ex);
            }
        }
        if (engine == null) {
            engine = checkForOSGiEngine(language);
        }
        if (engine != null && isPython(language)) {
            ScriptContext context = engine.getContext();
            context.setAttribute("com.sun.script.jython.comp.mode", "eval", ScriptContext.ENGINE_SCOPE);
        }
        return engine;
    }

    private static ScriptEngine checkForOSGiEngine(String language) {
        LOG.debug("No script engine found for {} using standard javax.script auto-registration. Checking OSGi registry.", language);
        try {
            // Test the OSGi environment with the Activator
            Class<?> c = Class.forName("org.apache.camel.script.osgi.Activator");
            Method mth = c.getDeclaredMethod("getBundleContext");
            Object ctx = mth.invoke(null);
            LOG.debug("Found OSGi BundleContext: {}", ctx);
            if (ctx != null) {
                Method resolveScriptEngine = c.getDeclaredMethod("resolveScriptEngine", String.class);
                return (ScriptEngine)resolveScriptEngine.invoke(null, language);
            }
        } catch (Throwable t) {
            LOG.debug("Unable to detect OSGi. ScriptEngine for " + language + " cannot be resolved.", t);
        }
        return null;
    }

    protected Object evaluateScript(Exchange exchange) {
        try {
            if (reuseScriptEngine(exchange)) {
                // It's not safe to do the evaluation with a single scriptEngine
                synchronized (this) {
                    LOG.debug("Calling doEvaluateScript without creating a new scriptEngine");
                    return doEvaluateScript(exchange, scriptEngine);
                }
            } else {
                LOG.debug("Calling doEvaluateScript with a new scriptEngine");
                // get a new engine which we must for each exchange
                ScriptEngine engine = scriptEngineFactory.getScriptEngine();
                return doEvaluateScript(exchange, engine);
            }
        } catch (ScriptException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Script evaluation failed: " + e.getMessage(), e);
            }
            if (e.getCause() != null) {
                throw createScriptEvaluationException(e.getCause());
            } else {
                throw createScriptEvaluationException(e);
            }
        } catch (IOException e) {
            throw createScriptEvaluationException(e);
        }
    }

    protected Object doEvaluateScript(Exchange exchange, ScriptEngine scriptEngine) throws ScriptException, IOException {
        ScriptContext context = populateBindings(scriptEngine, exchange, attributes);
        addScriptEngineArguments(scriptEngine, exchange);
        Object result = runScript(scriptEngine, exchange, context);
        LOG.debug("The script evaluation result is: {}", result);
        return result;
    }

    // To check the camel context property to decide if we need to reuse the ScriptEngine
    private boolean reuseScriptEngine(Exchange exchange) {
        CamelContext camelContext = exchange.getContext();
        if (camelContext != null) {
            return getCamelContextProperty(camelContext, Exchange.REUSE_SCRIPT_ENGINE);
        } else {
            // default value is false
            return false;
        }
    }

    private boolean compileScripte(CamelContext camelContext) {
        if (camelContext != null) {
            return getCamelContextProperty(camelContext, Exchange.COMPILE_SCRIPT);
        } else {
            return false;
        }
    }

    private boolean getCamelContextProperty(CamelContext camelContext, String propertyKey) {
        String propertyValue =  camelContext.getProperty(propertyKey);
        if (propertyValue != null) {
            return camelContext.getTypeConverter().convertTo(boolean.class, propertyValue);
        } else {
            return false;
        }
    }

    protected Object runScript(ScriptEngine engine, Exchange exchange, ScriptContext context) throws ScriptException, IOException {
        Object result = null;
        if (compiledScript != null) {
            LOG.trace("Evaluate using compiled script for context: {} on exchange: {}", context, exchange);
            result = compiledScript.eval(context);
        } else {
            if (scriptText != null) {
                LOG.trace("Evaluate script for context: {} on exchange: {}", context, exchange);
                result = engine.eval(scriptText, context);
            } else if (scriptResource != null) {
                Reader reader = createScriptReader(exchange.getContext().getClassResolver(), scriptResource);
                try {
                    LOG.trace("Evaluate script for context: {} on exchange: {}", context, exchange);
                    result = engine.eval(reader, context);
                } finally {
                    IOHelper.close(reader);
                }
            }
        }
        // As the script could have multiple statement, we need to look up the result from the engine value set
        if (result == null) {
            result = engine.get("result");
        }
        return result;
    }

    protected ScriptContext populateBindings(ScriptEngine engine, Exchange exchange, Map<String, Object> attributes) {
        ScriptContext context = engine.getContext();
        int scope = ScriptContext.ENGINE_SCOPE;
        context.setAttribute("context", exchange.getContext(), scope);
        context.setAttribute("camelContext", exchange.getContext(), scope);
        context.setAttribute("exchange", exchange, scope);
        Message in = exchange.getIn();
        context.setAttribute("request", in, scope);
        context.setAttribute("headers", in.getHeaders(), scope);
        context.setAttribute("body", in.getBody(), scope);
        if (exchange.hasOut()) {
            Message out = exchange.getOut();
            context.setAttribute("out", out, scope);
            context.setAttribute("response", out, scope);
        }
        // to make using properties component easier
        context.setAttribute("properties", new ScriptPropertiesFunction(exchange.getContext()), scope);
        // any additional attributes
        if (attributes != null) {
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                context.setAttribute(entry.getKey(), entry.getValue(), scope);
            }
        }
        return context;
    }

    @SuppressWarnings("unchecked")
    protected void addScriptEngineArguments(ScriptEngine engine, Exchange exchange) {
        if (!exchange.getIn().hasHeaders()) {
            return;
        }

        Map<Object, Object> args = exchange.getIn().getHeader(ARGUMENTS, Map.class);
        if (args != null) {
            for (Map.Entry<Object, Object> entry : args.entrySet()) {
                String key = exchange.getContext().getTypeConverter().convertTo(String.class, entry.getKey());
                Object value = entry.getValue();
                if (!ObjectHelper.isEmpty(key) && value != null) {
                    LOG.trace("Putting {} -> {} on ScriptEngine", key, value);
                    engine.put(key, value);
                }
            }
        }
    }

    protected static InputStreamReader createScriptReader(ClassResolver classResolver, String resource) throws IOException {
        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(classResolver, resource);
        return new InputStreamReader(is);
    }

    protected ScriptEvaluationException createScriptEvaluationException(Throwable e) {
        if (e.getClass().getName().equals("org.jruby.exceptions.RaiseException")) {
            // Only the nested exception has the specific problem
            try {
                Object ex = e.getClass().getMethod("getException").invoke(e);
                return new ScriptEvaluationException("Failed to evaluate: " + getScriptDescription() + ".  Error: " + ex + ". Cause: " + e, e);
            } catch (Exception e1) {
                // do nothing here
            }
        }
        return new ScriptEvaluationException("Failed to evaluate: " + getScriptDescription() + ". Cause: " + e, e);
    }

    private static boolean isPython(String language) {
        return "python".equals(language) || "jython".equals(language);
    }

    private static boolean isBeanShell(String language) {
        return "beanshell".equals(language) || "bsh".equals(language);
    }

}
