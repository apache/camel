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
package org.apache.camel.language.csimple;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StaticService;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Language("csimple")
public class CSimpleLanguage extends LanguageSupport implements StaticService {

    public static final String PRE_COMPILED_FILE = "META-INF/services/org/apache/camel/csimple.properties";
    public static final String CONFIG_FILE = "camel-csimple.properties";

    private static final Logger LOG = LoggerFactory.getLogger(CSimpleLanguage.class);

    private final Map<String, CSimpleExpression> compiled = new ConcurrentHashMap<>();
    private CSimpleCompiler compiler;

    private String configResource = "camel-csimple.properties";
    private Set<String> imports = new TreeSet<>();
    private Map<String, String> aliases = new HashMap<>();

    public String getConfigResource() {
        return configResource;
    }

    public void setConfigResource(String configResource) {
        this.configResource = configResource;
    }

    /**
     * Adds an import line
     *
     * @param imports import such as com.foo.MyClass
     */
    public void addImport(String imports) {
        if (!imports.startsWith("import ")) {
            imports = "import " + imports;
        }
        if (compiler != null) {
            compiler.addImport(imports);
        } else {
            this.imports.add(imports);
        }
    }

    /**
     * Adds an alias
     *
     * @param key   the key
     * @param value the value
     */
    public void addAliases(String key, String value) {
        if (compiler != null) {
            compiler.addAliases(key, value);
        } else {
            this.aliases.put(key, value);
        }
    }

    @Override
    public void init() {
        // load pre compiled first
        loadPreCompiled();

        // load optional configuration file
        loadConfiguration();

        // detect custom compiler (camel-csimple-joor)
        ExtendedCamelContext ecc = getCamelContext().adapt(ExtendedCamelContext.class);
        Optional<Class<?>> clazz = ecc.getBootstrapFactoryFinder().findClass(CSimpleCompiler.FACTORY);
        if (clazz.isPresent()) {
            compiler = (CSimpleCompiler) ecc.getInjector().newInstance(clazz.get(), false);
            if (compiler != null) {
                LOG.info("Detected camel-csimple-joor compiler");
                if (imports != null) {
                    imports.forEach(compiler::addImport);
                }
                if (aliases != null) {
                    aliases.forEach(compiler::addAliases);
                }
            }
            ServiceHelper.initService(compiler);
        }
    }

    @Override
    public void start() {
        ServiceHelper.startService(compiler);
    }

    @Override
    public void stop() {
        ServiceHelper.stopService(compiler);
    }

    private void loadPreCompiled() {
        ExtendedCamelContext ecc = getCamelContext().adapt(ExtendedCamelContext.class);
        InputStream is = ecc.getClassResolver().loadResourceAsStream(PRE_COMPILED_FILE);
        if (is != null) {
            try {
                String text = IOHelper.loadText(is);
                String[] lines = text.split("\n");
                for (String fqn : lines) {
                    // skip comments
                    fqn = fqn.trim();
                    if (fqn.startsWith("#") || fqn.isEmpty()) {
                        continue;
                    }
                    // load class
                    Class<CSimpleExpression> clazz = ecc.getClassResolver().resolveMandatoryClass(fqn, CSimpleExpression.class);
                    CSimpleExpression ce = clazz.getConstructor(CamelContext.class).newInstance(getCamelContext());
                    compiled.put(ce.getText(), ce);
                }
            } catch (Exception e) {
                throw new RuntimeCamelException("Error initializing csimple language", e);
            } finally {
                IOHelper.close(is);
            }
            if (!compiled.isEmpty()) {
                LOG.info("Loaded and initialized {} csimple expressions from classpath", compiled.size());
            }
        }
    }

    private void loadConfiguration() {
        InputStream is;
        String loaded;
        is = getCamelContext().getClassResolver().loadResourceAsStream(CONFIG_FILE);
        try {
            if (is == null) {
                // load from file system
                File file = new File(configResource);
                if (file.exists()) {
                    is = new FileInputStream(file);
                }
            }
            if (is == null) {
                return;
            }
            loaded = IOHelper.loadText(is);
        } catch (IOException e) {
            throw new RuntimeCamelException("Cannot load " + CONFIG_FILE + " from classpath");

        }
        IOHelper.close(is);

        int counter1 = 0;
        int counter2 = 0;
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
        if (counter1 > 0 || counter2 > 0) {
            LOG.info("Loaded csimple language imports: {} and aliases: {} from configuration: {}", counter1, counter2,
                    configResource);
        }
    }

    @Override
    public Predicate createPredicate(String expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression must be specified");
        }
        // text should be single line and trimmed as it can be multi lined
        String text = expression.replaceAll("\n", "");
        text = text.trim();

        Predicate answer = compiled.get(text);
        if (answer == null && compiler != null) {
            CSimpleExpression exp = compiler.compilePredicate(getCamelContext(), expression);
            if (exp != null) {
                compiled.put(text, exp);
                answer = exp;
            }
        }
        if (answer == null) {
            throw new CSimpleException("Cannot find compiled csimple language for predicate: " + expression, expression);
        }
        return answer;
    }

    @Override
    public Expression createExpression(String expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression must be specified");
        }
        // text should be single line and trimmed as it can be multi lined
        String text = expression.replaceAll("\n", "");
        text = text.trim();

        Expression answer = compiled.get(text);
        if (answer == null && compiler != null) {
            CSimpleExpression exp = compiler.compileExpression(getCamelContext(), expression);
            if (exp != null) {
                compiled.put(text, exp);
                answer = exp;
            }
        }
        if (answer == null) {
            throw new CSimpleException("Cannot find compiled csimple language for expression: " + expression, expression);
        }
        return answer;
    }

}
