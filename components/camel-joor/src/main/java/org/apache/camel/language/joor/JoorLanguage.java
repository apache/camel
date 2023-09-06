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
package org.apache.camel.language.joor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StaticService;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.ScriptHelper;
import org.apache.camel.support.TypedLanguageSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Language("joor")
public class JoorLanguage extends TypedLanguageSupport implements ScriptingLanguage, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(JoorLanguage.class);

    private static Boolean java8;
    private final JoorCompiler compiler;
    private final JoorScriptingCompiler scriptingCompiler;
    private final Set<String> imports = new TreeSet<>();
    private final Map<String, String> aliases = new HashMap<>();

    private String configResource = "classpath:camel-joor.properties?optional=true";
    private boolean preCompile = true;
    private boolean singleQuotes = true;

    public JoorLanguage() {
        this(new JoorCompiler(), new JoorScriptingCompiler());
    }

    public JoorLanguage(JoorCompiler compiler, JoorScriptingCompiler scriptingCompiler) {
        this.compiler = compiler;
        this.scriptingCompiler = scriptingCompiler;
    }

    public JoorCompiler getCompiler() {
        return compiler;
    }

    public JoorScriptingCompiler getScriptingCompiler() {
        return scriptingCompiler;
    }

    public String getConfigResource() {
        return configResource;
    }

    public void setConfigResource(String configResource) {
        this.configResource = configResource;
        // trigger configuration to be re-loaded
        loadConfiguration();
    }

    public boolean isPreCompile() {
        return preCompile;
    }

    public void setPreCompile(boolean preCompile) {
        this.preCompile = preCompile;
    }

    public boolean isSingleQuotes() {
        return singleQuotes;
    }

    public void setSingleQuotes(boolean singleQuotes) {
        this.singleQuotes = singleQuotes;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T evaluate(String script, Map<String, Object> bindings, Class<T> resultType) {
        Object out;
        JoorScriptingMethod target = scriptingCompiler.compile(getCamelContext(), script, bindings, singleQuotes);
        try {
            out = target.evaluate(bindings);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
        if (out != null && resultType != null) {
            return getCamelContext().getTypeConverter().convertTo(resultType, out);
        } else {
            return (T) out;
        }
    }

    @Override
    public Predicate createPredicate(String expression) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression));
    }

    @Override
    public Expression createExpression(String expression) {
        JoorExpression exp = new JoorExpression(expression);
        exp.setCompiler(compiler);
        exp.setResultType(getResultType());
        exp.setSingleQuotes(singleQuotes);
        exp.init(getCamelContext());
        return exp;
    }

    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        return (JoorExpression) createExpression(expression, properties);
    }

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        JoorExpression exp = new JoorExpression(expression);
        exp.setCompiler(compiler);
        exp.setPreCompile(property(boolean.class, properties, 0, preCompile));
        exp.setResultType(property(Class.class, properties, 1, getResultType()));
        exp.setSingleQuotes(property(boolean.class, properties, 2, singleQuotes));
        exp.init(getCamelContext());
        return exp;
    }

    @Override
    public void init() {
        // attempt to load optional configuration from classpath
        loadConfiguration();
    }

    @Override
    public void start() {
        ServiceHelper.startService(compiler);
    }

    @Override
    public void stop() {
        ServiceHelper.stopService(compiler);
    }

    private void loadConfiguration() {
        // attempt to load configuration
        String loaded = ScriptHelper.resolveOptionalExternalScript(getCamelContext(), "resource:" + configResource);
        int counter1 = 0;
        int counter2 = 0;
        if (loaded != null) {
            String[] lines = loaded.split("\n");
            for (String line : lines) {
                line = line.trim();
                // skip comments
                if (line.startsWith("#")) {
                    continue;
                }
                // imports
                if (line.startsWith("import ")) {
                    imports.add(line);
                    counter1++;
                    continue;
                }
                // aliases as key=value
                String key = StringHelper.before(line, "=");
                String value = StringHelper.after(line, "=");
                if (key != null) {
                    key = key.trim();
                }
                if (value != null) {
                    value = value.trim();
                }
                if (key != null && value != null) {
                    this.aliases.put(key, value);
                    counter2++;
                }
            }
        }
        if (counter1 > 0 || counter2 > 0) {
            LOG.info("Loaded jOOR language imports: {} and aliases: {} from configuration: {}", counter1, counter2,
                    configResource);
        }
        if (compiler.getAliases() == null) {
            compiler.setAliases(aliases);
        } else {
            compiler.getAliases().putAll(aliases);
        }
        if (compiler.getImports() == null) {
            compiler.setImports(imports);
        } else {
            compiler.getImports().addAll(imports);
        }
        if (scriptingCompiler.getAliases() == null) {
            scriptingCompiler.setAliases(aliases);
        } else {
            scriptingCompiler.getAliases().putAll(aliases);
        }
        if (scriptingCompiler.getImports() == null) {
            scriptingCompiler.setImports(imports);
        } else {
            scriptingCompiler.getImports().addAll(imports);
        }
    }
}
