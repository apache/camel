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

import java.lang.invoke.MethodHandles;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.StaticService;
import org.apache.camel.spi.CompileStrategy;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ScriptHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JoorScriptingCompiler extends ServiceSupport implements StaticService, CamelContextAware {

    private static final Pattern BEAN_INJECTION_PATTERN = Pattern.compile("(#bean:)([A-Za-z0-9-_]*)");

    private static final Logger LOG = LoggerFactory.getLogger(JoorScriptingCompiler.class);
    private static final AtomicInteger UUID = new AtomicInteger();
    private CamelContext camelContext;
    private JavaJoorClassLoader classLoader;
    private Set<String> imports = new TreeSet<>();
    private Map<String, String> aliases;
    private int counter;
    private long taken;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public Set<String> getImports() {
        return imports;
    }

    public void setImports(Set<String> imports) {
        this.imports = imports;
    }

    public Map<String, String> getAliases() {
        return aliases;
    }

    public void setAliases(Map<String, String> aliases) {
        this.aliases = aliases;
    }

    @Override
    protected void doBuild() throws Exception {
        // register jOOR classloader to camel, so we are able to load classes we have compiled
        CamelContext context = getCamelContext();
        if (context != null) {
            // use existing class loader if available
            classLoader = (JavaJoorClassLoader) context.getClassResolver().getClassLoader("JavaJoorClassLoader");
            if (classLoader == null) {
                classLoader = new JavaJoorClassLoader();
                context.getClassResolver().addClassLoader(classLoader);
            }
            // use work dir for classloader as it writes compiled classes to disk
            CompileStrategy cs = context.getCamelContextExtension().getContextPlugin(CompileStrategy.class);
            if (cs != null && cs.getWorkDir() != null) {
                classLoader.setCompileDirectory(cs.getWorkDir());
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (counter > 0) {
            LOG.info("Java language compiled {} scripts in {} millis", counter, taken);
        }
    }

    public JoorScriptingMethod compile(
            CamelContext camelContext, String script, Map<String, Object> bindings, boolean singleQuotes) {
        StopWatch watch = new StopWatch();

        JoorScriptingMethod answer;
        String className = nextFQN();
        String code = evalCode(camelContext, className, script, bindings, singleQuotes);
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Compiling code:\n\n{}\n", code);
            }
            CompilationUnit unit = CompilationUnit.input();
            unit.addClass(className, code);

            // include classloader from Camel, so we can load any already compiled and loaded classes
            ClassLoader parent = MethodHandles.lookup().lookupClass().getClassLoader();
            if (parent instanceof URLClassLoader ucl) {
                ClassLoader cl = new CamelJoorClassLoader(ucl, camelContext);
                unit.withClassLoader(cl);
            }
            LOG.debug("Compiling: {}", className);

            CompilationUnit.Result result = MultiCompile.compileUnit(unit);
            Class<?> clazz = result.getClass(className);
            if (clazz != null) {
                LOG.debug("Compiled to Java class: {}", clazz);
                answer = (JoorScriptingMethod) clazz.getConstructor(CamelContext.class).newInstance(camelContext);
            } else {
                answer = null;
            }
        } catch (Exception e) {
            throw new JoorCompilationException(className, code, e);
        }

        counter++;
        taken += watch.taken();
        return answer;
    }

    @SuppressWarnings("unchecked")
    public String evalCode(
            CamelContext camelContext, String fqn, String script, Map<String, Object> bindings,
            boolean singleQuotes) {
        String qn = fqn.substring(0, fqn.lastIndexOf('.'));
        String name = fqn.substring(fqn.lastIndexOf('.') + 1);

        // reload script
        script = ScriptHelper.resolveOptionalExternalScript(camelContext, script);

        // trim text
        script = script.trim();
        // special for evaluating aggregation strategy via a BiFunction
        boolean biFunction = script.startsWith("(e1, e2) ->");

        Set<String> scriptImports = new LinkedHashSet<>();
        Map<String, Class> scriptBeans = new HashMap<>();
        script = evalDependencyInjection(camelContext, scriptImports, scriptBeans, script);

        //  wrap text into a class method we can call
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(qn).append(";\n");
        sb.append("\n");
        sb.append("import java.util.*;\n");
        sb.append("import java.util.concurrent.*;\n");
        sb.append("import java.util.function.*;\n");
        sb.append("import java.util.stream.*;\n");
        sb.append("\n");
        sb.append("import org.apache.camel.*;\n");
        sb.append("import org.apache.camel.util.*;\n");
        sb.append("import static org.apache.camel.language.joor.JoorHelper.*;\n");
        sb.append("\n");
        // custom imports
        for (String i : imports) {
            sb.append(i);
            if (!i.endsWith(";")) {
                sb.append(";");
            }
            sb.append("\n");
        }
        for (String i : scriptImports) {
            sb.append("import ");
            sb.append(i);
            sb.append(";\n");
        }
        sb.append("\n");
        sb.append("public class ").append(name).append(" implements org.apache.camel.language.joor.JoorScriptingMethod {\n");
        sb.append("\n");
        // local beans variables
        for (Map.Entry<String, Class> entry : scriptBeans.entrySet()) {
            sb.append("    private ").append(entry.getValue().getSimpleName()).append(" ").append(entry.getKey()).append(";\n");
        }
        sb.append("\n");

        // constructor to lookup beans
        sb.append("    public ").append(name).append("(CamelContext context) throws Exception {\n");
        for (Map.Entry<String, Class> entry : scriptBeans.entrySet()) {
            sb.append("        ").append(entry.getKey()).append(" = ").append("context.getRegistry().lookupByNameAndType(\"")
                    .append(entry.getKey()).append("\", ").append(entry.getValue().getSimpleName()).append(".class);\n");
        }
        sb.append("    }\n");
        sb.append("\n");

        sb.append("    @Override\n");
        sb.append(
                "    public Object evaluate(Map<String, Object> args) throws Exception {\n");
        sb.append("        ");

        // bindings as local variables
        if (bindings != null) {
            for (Map.Entry<String, Object> bind : bindings.entrySet()) {
                String vn = bind.getKey();
                String cn = ObjectHelper.className(bind.getValue());
                String b = String.format("        var %s = (%s) args.get(\"%s\");%n", vn, cn, vn);
                sb.append(b);
            }
            sb.append("\n");
        }
        if (!script.contains("return ")) {
            sb.append("return ");
        }
        if (biFunction) {
            if (!sb.toString().endsWith("return ")) {
                sb.append("return ");
            }
            sb.append("(BiFunction<Exchange, Exchange, Object>) ");
        }

        if (singleQuotes) {
            // single quotes instead of double quotes, as its very annoying for string in strings
            String quoted = script.replace('\'', '"');
            sb.append(quoted);
        } else {
            sb.append(script);
        }
        if (!script.endsWith("}") && !script.endsWith(";")) {
            sb.append(";");
        }
        if (biFunction && !script.endsWith(";")) {
            sb.append(";");
        }
        sb.append("\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("\n");

        return sb.toString();
    }

    private String evalDependencyInjection(
            CamelContext camelContext, Set<String> scriptImports, Map<String, Class> scriptBeans, String script) {
        Matcher matcher = BEAN_INJECTION_PATTERN.matcher(script);
        while (matcher.find()) {
            String id = matcher.group(2);
            Object bean = CamelContextHelper.mandatoryLookup(camelContext, id);
            Class<?> type = bean.getClass();
            scriptImports.add(type.getName());
            scriptBeans.put(id, type);
            script = matcher.replaceFirst(id);
            matcher = BEAN_INJECTION_PATTERN.matcher(script);
        }
        return script;
    }

    private static String nextFQN() {
        return "org.apache.camel.language.joor.compiled.scripting.JoorScripting" + UUID.incrementAndGet();
    }

}
