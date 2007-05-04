/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.builder.script;

import static org.apache.camel.util.ObjectHelper.notNull;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.converter.ObjectConverter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

/**
 * A builder class for creating {@link Processor}, {@link Expression} and {@link Predicate} objects using
 * the JSR 223 scripting engine.
 *
 * @version $Revision$
 */
public class ScriptBuilder<E extends Exchange> implements Expression<E>, Predicate<E>, Processor {
    private static final transient Log log = LogFactory.getLog(ScriptBuilder.class);

    private String scriptEngineName;
    private Resource scriptResource;
    private String scriptText;
    private ScriptEngine engine;
    private CompiledScript compiledScript;

    public ScriptBuilder(String scriptEngineName) {
        this.scriptEngineName = scriptEngineName;
    }

    public ScriptBuilder(String scriptEngineName, String scriptText) {
        this(scriptEngineName);
        this.scriptText = scriptText;
    }

    public ScriptBuilder(String scriptEngineName, Resource scriptResource) {
        this(scriptEngineName);
        this.scriptResource = scriptResource;
    }

    @Override
    public String toString() {
        return getScriptDescription();
    }

    public Object evaluate(E exchange) {
        return evaluateScript(exchange);
    }

    public boolean matches(E exchange) {
        Object scriptValue = evaluateScript(exchange);
        return matches(exchange, scriptValue);
    }

    public void assertMatches(String text, E exchange) throws AssertionError {
        Object scriptValue = evaluateScript(exchange);
        if (!matches(exchange, scriptValue)) {
            throw new AssertionError(this + " failed on " + exchange + " as script returned <" + scriptValue + ">");
        }
    }

    public void process(Exchange exchange) {
        evaluateScript(exchange);
    }

    
    // Builder API
    //-------------------------------------------------------------------------

    /**
     * Sets the attribute on the context so that it is available to the script as a variable
     * in the {@link ScriptContext#ENGINE_SCOPE}
     *
     * @param name the name of the attribute
     * @param value the attribute value
     * @return this builder
     */
    public ScriptBuilder attribute(String name, Object value) {
        getScriptContext().setAttribute(name, value, ScriptContext.ENGINE_SCOPE);
        return this;
    }


    // Create any scripting language builder recognised by JSR 223
    //-------------------------------------------------------------------------

    /**
     * Creates a script builder for the named language and script contents
     *
     * @param language the language to use for the script
     * @param scriptText the script text to be evaluted
     * @return the builder
     */
    public static ScriptBuilder script(String language, String scriptText) {
        return new ScriptBuilder(language, scriptText);
    }

    /**
     * Creates a script builder for the named language and script @{link Resource}
     *
     * @param language the language to use for the script
     * @param scriptResource the resource used to load the script
     * @return the builder
     */
    public static ScriptBuilder script(String language, Resource scriptResource) {
        return new ScriptBuilder(language, scriptResource);
    }

    /**
     * Creates a script builder for the named language and script @{link File}
     *
     * @param language the language to use for the script
     * @param scriptFile the file used to load the script
     * @return the builder
     */
    public static ScriptBuilder script(String language, File scriptFile) {
        return new ScriptBuilder(language, new FileSystemResource(scriptFile));
    }

    /**
     * Creates a script builder for the named language and script @{link URL}
     *
     * @param language the language to use for the script
     * @param scriptURL the URL used to load the script
     * @return the builder
     */
    public static ScriptBuilder script(String language, URL scriptURL) {
        return new ScriptBuilder(language, new UrlResource(scriptURL));
    }


    // Groovy
    //-------------------------------------------------------------------------

    /**
     * Creates a script builder for the groovy script contents
     *
     * @param scriptText the script text to be evaluted
     * @return the builder
     */
    public static ScriptBuilder groovy(String scriptText) {
        return new ScriptBuilder("groovy", scriptText);
    }

    /**
     * Creates a script builder for the groovy script @{link Resource}
     *
     * @param scriptResource the resource used to load the script
     * @return the builder
     */
    public static ScriptBuilder groovy(Resource scriptResource) {
        return new ScriptBuilder("groovy", scriptResource);
    }

    /**
     * Creates a script builder for the groovy script @{link File}
     *
     * @param scriptFile the file used to load the script
     * @return the builder
     */
    public static ScriptBuilder groovy(File scriptFile) {
        return new ScriptBuilder("groovy", new FileSystemResource(scriptFile));
    }

    /**
     * Creates a script builder for the groovy script @{link URL}
     *
     * @param scriptURL the URL used to load the script
     * @return the builder
     */
    public static ScriptBuilder groovy(URL scriptURL) {
        return new ScriptBuilder("groovy", new UrlResource(scriptURL));
    }


    // JavaScript
    //-------------------------------------------------------------------------

    /**
     * Creates a script builder for the JavaScript/ECMAScript script contents
     *
     * @param scriptText the script text to be evaluted
     * @return the builder
     */
    public static ScriptBuilder javaScript(String scriptText) {
        return new ScriptBuilder("js", scriptText);
    }

    /**
     * Creates a script builder for the JavaScript/ECMAScript script @{link Resource}
     *
     * @param scriptResource the resource used to load the script
     * @return the builder
     */
    public static ScriptBuilder javaScript(Resource scriptResource) {
        return new ScriptBuilder("js", scriptResource);
    }

    /**
     * Creates a script builder for the JavaScript/ECMAScript script @{link File}
     *
     * @param scriptFile the file used to load the script
     * @return the builder
     */
    public static ScriptBuilder javaScript(File scriptFile) {
        return new ScriptBuilder("js", new FileSystemResource(scriptFile));
    }

    /**
     * Creates a script builder for the JavaScript/ECMAScript script @{link URL}
     *
     * @param scriptURL the URL used to load the script
     * @return the builder
     */
    public static ScriptBuilder javaScript(URL scriptURL) {
        return new ScriptBuilder("js", new UrlResource(scriptURL));
    }



    // PHP
    //-------------------------------------------------------------------------

    /**
     * Creates a script builder for the PHP script contents
     *
     * @param scriptText the script text to be evaluted
     * @return the builder
     */
    public static ScriptBuilder php(String scriptText) {
        return new ScriptBuilder("php", scriptText);
    }

    /**
     * Creates a script builder for the PHP script @{link Resource}
     *
     * @param scriptResource the resource used to load the script
     * @return the builder
     */
    public static ScriptBuilder php(Resource scriptResource) {
        return new ScriptBuilder("php", scriptResource);
    }

    /**
     * Creates a script builder for the PHP script @{link File}
     *
     * @param scriptFile the file used to load the script
     * @return the builder
     */
    public static ScriptBuilder php(File scriptFile) {
        return new ScriptBuilder("php", new FileSystemResource(scriptFile));
    }

    /**
     * Creates a script builder for the PHP script @{link URL}
     *
     * @param scriptURL the URL used to load the script
     * @return the builder
     */
    public static ScriptBuilder php(URL scriptURL) {
        return new ScriptBuilder("php", new UrlResource(scriptURL));
    }



    // Python
    //-------------------------------------------------------------------------

    /**
     * Creates a script builder for the Python script contents
     *
     * @param scriptText the script text to be evaluted
     * @return the builder
     */
    public static ScriptBuilder python(String scriptText) {
        return new ScriptBuilder("python", scriptText);
    }

    /**
     * Creates a script builder for the Python script @{link Resource}
     *
     * @param scriptResource the resource used to load the script
     * @return the builder
     */
    public static ScriptBuilder python(Resource scriptResource) {
        return new ScriptBuilder("python", scriptResource);
    }

    /**
     * Creates a script builder for the Python script @{link File}
     *
     * @param scriptFile the file used to load the script
     * @return the builder
     */
    public static ScriptBuilder python(File scriptFile) {
        return new ScriptBuilder("python", new FileSystemResource(scriptFile));
    }

    /**
     * Creates a script builder for the Python script @{link URL}
     *
     * @param scriptURL the URL used to load the script
     * @return the builder
     */
    public static ScriptBuilder python(URL scriptURL) {
        return new ScriptBuilder("python", new UrlResource(scriptURL));
    }


    // Ruby/JRuby
    //-------------------------------------------------------------------------

    /**
     * Creates a script builder for the Ruby/JRuby script contents
     *
     * @param scriptText the script text to be evaluted
     * @return the builder
     */
    public static ScriptBuilder ruby(String scriptText) {
        return new ScriptBuilder("jruby", scriptText);
    }

    /**
     * Creates a script builder for the Ruby/JRuby script @{link Resource}
     *
     * @param scriptResource the resource used to load the script
     * @return the builder
     */
    public static ScriptBuilder ruby(Resource scriptResource) {
        return new ScriptBuilder("jruby", scriptResource);
    }

    /**
     * Creates a script builder for the Ruby/JRuby script @{link File}
     *
     * @param scriptFile the file used to load the script
     * @return the builder
     */
    public static ScriptBuilder ruby(File scriptFile) {
        return new ScriptBuilder("jruby", new FileSystemResource(scriptFile));
    }

    /**
     * Creates a script builder for the Ruby/JRuby script @{link URL}
     *
     * @param scriptURL the URL used to load the script
     * @return the builder
     */
    public static ScriptBuilder ruby(URL scriptURL) {
        return new ScriptBuilder("jruby", new UrlResource(scriptURL));
    }


    // Properties
    //-------------------------------------------------------------------------
    public ScriptEngine getEngine() {
        checkInitialised();
        return engine;
    }

    public CompiledScript getCompiledScript() {
        return compiledScript;
    }

    public String getScriptText() {
        return scriptText;
    }

    public void setScriptText(String scriptText) {
        this.scriptText = scriptText;
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
        }
        else if (scriptResource != null) {
            return scriptEngineName + ": " + scriptResource.getDescription();
        }
        else {
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

    public Resource getScriptResource() {
        return scriptResource;
    }

    public void setScriptResource(Resource scriptResource) {
        this.scriptResource = scriptResource;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected void checkInitialised() {
        if (scriptText == null && scriptResource == null) {
            throw new IllegalArgumentException("Neither scriptText or scriptResource are specified");
        }
        if (engine == null) {
            engine = createScriptEngine();
        }
        if (compiledScript == null) {
            if (engine instanceof Compilable) {
                compileScript((Compilable) engine);
            }
        }
    }
    
    protected boolean matches(E exchange, Object scriptValue) {
        return ObjectConverter.toBoolean(scriptValue);
    }

    protected ScriptEngine createScriptEngine() {
        ScriptEngineManager manager = new ScriptEngineManager();
        return manager.getEngineByName(scriptEngineName);
    }

    protected void compileScript(Compilable compilable) {
        try {
            if (scriptText != null) {
                compiledScript = compilable.compile(scriptText);
            }
            else if (scriptResource != null) {
                compiledScript = compilable.compile(createScriptReader());
            }
        }
        catch (ScriptException e) {
            if (log.isDebugEnabled()) {
                log.debug("Script compile failed: " + e, e);
            }
            throw createScriptCompileException(e);
        }
        catch (IOException e) {
            throw createScriptCompileException(e);
        }
    }

    protected synchronized Object evaluateScript(Exchange exchange) {
        try {
            getScriptContext();
            populateBindings(getEngine(), exchange);
            return runScript();
        }
        catch (ScriptException e) {
            if (log.isDebugEnabled()) {
                log.debug("Script evaluation failed: " + e, e);
            }
            throw createScriptEvaluationException(e.getCause());
        }
        catch (IOException e) {
            throw createScriptEvaluationException(e);
        }
    }


    protected Object runScript() throws ScriptException, IOException {
        checkInitialised();
        if (compiledScript != null) {
            return compiledScript.eval();
        }
        else {
            if (scriptText != null) {
                return getEngine().eval(scriptText);
            }
            else {
                return getEngine().eval(createScriptReader());
            }
        }
    }

    protected void populateBindings(ScriptEngine engine, Exchange exchange) {
        ScriptContext context = engine.getContext();
        int scope = ScriptContext.ENGINE_SCOPE;
        context.setAttribute("context", exchange.getContext(), scope);
        context.setAttribute("exchange", exchange, scope);
        context.setAttribute("request", exchange.getIn(), scope);
        context.setAttribute("response", exchange.getOut(), scope);
    }

    protected InputStreamReader createScriptReader() throws IOException {
        // TODO consider character sets?
        return new InputStreamReader(scriptResource.getInputStream());
    }

    protected ScriptEvaluationException createScriptCompileException(Exception e) {
        return new ScriptEvaluationException("Failed to compile: " + getScriptDescription() + ". Cause: " + e, e);
    }

    protected ScriptEvaluationException createScriptEvaluationException(Throwable e) {
        return new ScriptEvaluationException("Failed to evaluate: " + getScriptDescription() + ". Cause: " + e, e);
    }

}
