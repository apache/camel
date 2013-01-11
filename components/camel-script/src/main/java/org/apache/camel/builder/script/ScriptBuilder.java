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
import java.lang.reflect.Method;
import java.util.Map;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.converter.ObjectConverter;
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

    private static final transient Logger LOG = LoggerFactory.getLogger(ScriptBuilder.class);

    private String scriptEngineName;
    private String scriptResource;
    private String scriptText;
    private ScriptEngine engine;
    private CompiledScript compiledScript;

    /**
     * Constructor.
     *
     * @param scriptEngineName the name of the scripting language
     */
    public ScriptBuilder(String scriptEngineName) {
        this.scriptEngineName = scriptEngineName;
    }

    /**
     * Constructor.
     *
     * @param scriptEngineName the name of the scripting language
     * @param scriptText the script text to be evaluated, or a reference to a script resource
     */
    public ScriptBuilder(String scriptEngineName, String scriptText) {
        this(scriptEngineName);
        setScriptText(scriptText);
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
        getScriptContext().setAttribute(name, value, ScriptContext.ENGINE_SCOPE);
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

    // Properties
    // -------------------------------------------------------------------------

    public ScriptEngine getEngine() {
        if (engine == null) {
            engine = createScriptEngine();
        }
        if (engine == null) {
            throw new IllegalArgumentException("No script engine could be created for: " + getScriptEngineName());
        }
        return engine;
    }

    public CompiledScript getCompiledScript() {
        return compiledScript;
    }

    public String getScriptText() {
        return scriptText;
    }

    public void setScriptText(String scriptText) {
        if (ResourceHelper.hasScheme(scriptText)) {
            this.scriptResource = scriptText;
        } else {
            this.scriptText = scriptText;
        }
    }

    public String getScriptEngineName() {
        return scriptEngineName;
    }

    /**
     * Returns a description of the script
     *
     * @return the script description
     */
    public String getScriptDescription() {
        if (scriptText != null) {
            return scriptEngineName + ": " + scriptText;
        } else if (scriptResource != null) {
            return scriptEngineName + ": " + scriptResource;
        } else {
            return scriptEngineName + ": null script";
        }
    }

    /**
     * Access the script context so that it can be configured such as adding attributes
     */
    public ScriptContext getScriptContext() {
        return getEngine().getContext();
    }

    /**
     * Sets the context to use by the script
     */
    public void setScriptContext(ScriptContext scriptContext) {
        getEngine().setContext(scriptContext);
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    protected void checkInitialised(Exchange exchange) {
        if (scriptText == null && scriptResource == null) {
            throw new IllegalArgumentException("Neither scriptText or scriptResource are specified");
        }
        if (engine == null) {
            engine = createScriptEngine();
        }
        if (compiledScript == null) {
            // BeanShell implements Compilable but throws an exception if you call compile
            if (engine instanceof Compilable && !isBeanShell()) { 
                compileScript((Compilable)engine, exchange);
            }
        }
    }

    protected boolean matches(Exchange exchange, Object scriptValue) {
        return ObjectConverter.toBool(scriptValue);
    }

    protected ScriptEngine createScriptEngine() {
        ScriptEngineManager manager = new ScriptEngineManager();
        try {
            engine = manager.getEngineByName(scriptEngineName);
        } catch (NoClassDefFoundError ex) {
            LOG.error("Cannot load the scriptEngine for " + scriptEngineName + ", the exception is " + ex
                      + ", please ensure correct JARs is provided on classpath.");
        }
        if (engine == null) {
            engine = checkForOSGiEngine();
        }
        if (engine == null) {
            throw new IllegalArgumentException("No script engine could be created for: " + getScriptEngineName());
        }
        if (isPython()) {
            ScriptContext context = engine.getContext();
            context.setAttribute("com.sun.script.jython.comp.mode", "eval", ScriptContext.ENGINE_SCOPE);
        }
        return engine;
    }

    private ScriptEngine checkForOSGiEngine() {
        LOG.debug("No script engine found for " + scriptEngineName + " using standard javax.script auto-registration.  Checking OSGi registry...");
        try {
            // Test the OSGi environment with the Activator
            Class<?> c = Class.forName("org.apache.camel.script.osgi.Activator");
            Method mth = c.getDeclaredMethod("getBundleContext");
            Object ctx = mth.invoke(null);
            LOG.debug("Found OSGi BundleContext " + ctx);
            if (ctx != null) {
                Method resolveScriptEngine = c.getDeclaredMethod("resolveScriptEngine", String.class);
                return (ScriptEngine)resolveScriptEngine.invoke(null, scriptEngineName);
            }
        } catch (Throwable t) {
            LOG.debug("Unable to load OSGi, script engine cannot be found", t);
        }
        return null;
    }

    protected void compileScript(Compilable compilable, Exchange exchange) {
        Reader reader = null;
        try {
            if (scriptText != null) {
                compiledScript = compilable.compile(scriptText);
            } else if (scriptResource != null) {
                reader = createScriptReader(exchange);
                compiledScript = compilable.compile(reader);
            }
        } catch (ScriptException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Script compile failed: " + e.getMessage(), e);
            }
            throw createScriptCompileException(e);
        } catch (IOException e) {
            throw createScriptCompileException(e);
        } finally {
            IOHelper.close(reader);
        }
    }

    protected synchronized Object evaluateScript(Exchange exchange) {
        try {
            getScriptContext();
            populateBindings(getEngine(), exchange);
            addScriptEngineArguments(getEngine(), exchange);
            Object result = runScript(exchange);
            LOG.debug("The script evaluation result is: {}", result);
            return result;
        } catch (ScriptException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Script evaluation failed: " + e.getMessage(), e);
            }
            throw createScriptEvaluationException(e.getCause());
        } catch (IOException e) {
            throw createScriptEvaluationException(e);
        }
    }

    protected Object runScript(Exchange exchange) throws ScriptException, IOException {
        checkInitialised(exchange);
        Object result;
        if (compiledScript != null) {
            result = compiledScript.eval();
        } else {
            if (scriptText != null) {
                result = getEngine().eval(scriptText);
            } else {
                result = getEngine().eval(createScriptReader(exchange));
            }
        }
        return result;
    }

    protected void populateBindings(ScriptEngine engine, Exchange exchange) {
        ScriptContext context = engine.getContext();
        int scope = ScriptContext.ENGINE_SCOPE;
        context.setAttribute("context", exchange.getContext(), scope);
        context.setAttribute("camelContext", exchange.getContext(), scope);
        context.setAttribute("exchange", exchange, scope);
        Message in = exchange.getIn();
        context.setAttribute("in", in, scope);
        context.setAttribute("request", in, scope);
        context.setAttribute("headers", in.getHeaders(), scope);
        if (exchange.hasOut()) {
            Message out = exchange.getOut();
            context.setAttribute("out", out , scope);
            context.setAttribute("response", out, scope);
        }
        // to make using properties component easier
        context.setAttribute("properties", new ScriptPropertiesFunction(exchange.getContext()), scope);
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

    protected InputStreamReader createScriptReader(Exchange exchange) throws IOException {
        ObjectHelper.notNull(scriptResource, "scriptResource", this);
        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(exchange.getContext().getClassResolver(), scriptResource);
        return new InputStreamReader(is);
    }

    protected ScriptEvaluationException createScriptCompileException(Exception e) {
        return new ScriptEvaluationException("Failed to compile: " + getScriptDescription() + ". Cause: " + e, e);
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

    protected boolean isPython() {
        return "python".equals(scriptEngineName) || "jython".equals(scriptEngineName);
    }

    protected boolean isBeanShell() {
        return "beanshell".equals(scriptEngineName) || "bsh".equals(scriptEngineName);
    }
}
