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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.StaticService;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ScriptHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.joor.Reflect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JoorCompiler extends ServiceSupport implements StaticService {

    private static final Pattern BEAN_INJECTION_PATTERN = Pattern.compile("(#bean:)([A-Za-z0-9-_]*)");
    private static final Pattern BODY_AS_PATTERN = Pattern.compile("(optionalBodyAs|bodyAs)\\(([A-Za-z0-9.$]*)(.class)\\)");
    private static final Pattern BODY_AS_PATTERN_NO_CLASS = Pattern.compile("(optionalBodyAs|bodyAs)\\(([A-Za-z0-9.$]*)\\)");
    private static final Pattern HEADER_AS_PATTERN
            = Pattern.compile("(optionalHeaderAs|headerAs)\\((['|\"][A-Za-z0-9.$]*['|\"]\\s*),\\s*([A-Za-z0-9.$]*.class)\\)");
    private static final Pattern HEADER_AS_PATTERN_NO_CLASS
            = Pattern.compile("(optionalHeaderAs|headerAs)\\((['|\"][A-Za-z0-9.$]*['|\"])\\s*,\\s*([A-Za-z0-9.$]*)\\)");
    private static final Pattern HEADER_AS_DEFAULT_VALUE_PATTERN
            = Pattern.compile("(headerAs)\\((['|\"][A-Za-z0-9.$]*['|\"])\\s*,(.+),\\s*([A-Za-z0-9.$]*.class)\\)");
    private static final Pattern HEADER_AS_DEFAULT_VALUE_PATTERN_NO_CLASS
            = Pattern.compile("(headerAs)\\((['|\"][A-Za-z0-9.$]*['|\"])\\s*,(.+),\\s*([A-Za-z0-9.$]*)\\)");
    private static final Pattern EXCHANGE_PROPERTY_AS_PATTERN
            = Pattern.compile(
                    "(optionalExchangePropertyAs|exchangePropertyAs)\\((['|\"][A-Za-z0-9.$]*['|\"])\\s*,\\s*([A-Za-z0-9.$]*.class)\\)");
    private static final Pattern EXCHANGE_PROPERTY_AS_PATTERN_NO_CLASS
            = Pattern.compile(
                    "(optionalExchangePropertyAs|exchangePropertyAs)\\((['|\"][A-Za-z0-9.$]*['|\"])\\s*,\\s*([A-Za-z0-9.$]*)\\)");
    private static final Pattern EXCHANGE_PROPERTY_AS_DEFAULT_VALUE_PATTERN
            = Pattern.compile(
                    "(exchangePropertyAs)\\((['|\"][A-Za-z0-9.$]*['|\"])\\s*,(.+),\\s*([A-Za-z0-9.$]*.class)\\)");
    private static final Pattern EXCHANGE_PROPERTY_AS_DEFAULT_VALUE_PATTERN_NO_CLASS
            = Pattern.compile(
                    "(exchangePropertyAs)\\((['|\"][A-Za-z0-9.$]*['|\"])\\s*,(.+),\\s*([A-Za-z0-9.$]*)\\)");

    private static final Logger LOG = LoggerFactory.getLogger(JoorCompiler.class);
    private static final AtomicInteger UUID = new AtomicInteger();
    private Set<String> imports = new TreeSet<>();
    private Map<String, String> aliases;
    private int counter;
    private long taken;

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
    protected void doStop() throws Exception {
        super.doStop();
        if (counter > 0) {
            LOG.debug("Java compiled {} {} in {}", counter, counter == 1 ? "script" : "scripts",
                    TimeUtils.printDuration(taken, true));
        }
    }

    public JoorMethod compile(CamelContext camelContext, String script, boolean singleQuotes) {
        StopWatch watch = new StopWatch();

        JoorMethod answer;
        String className = nextFQN();
        String code = evalCode(camelContext, className, script, singleQuotes);
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Compiling code:\n\n{}\n", code);
            }
            Reflect ref = Reflect.compile(className, code);
            Class<?> clazz = ref.type();
            answer = (JoorMethod) clazz.getConstructor(CamelContext.class).newInstance(camelContext);
        } catch (Exception e) {
            throw new JoorCompilationException(className, code, e);
        }

        counter++;
        taken += watch.taken();
        return answer;
    }

    public String evalCode(CamelContext camelContext, String fqn, String script, boolean singleQuotes) {
        String qn = fqn.substring(0, fqn.lastIndexOf('.'));
        String name = fqn.substring(fqn.lastIndexOf('.') + 1);

        // reload script
        script = ScriptHelper.resolveOptionalExternalScript(camelContext, script);

        // trim text
        script = script.trim();
        // special for evaluating aggregation strategy via a BiFunction
        boolean biFunction = script.startsWith("(e1, e2) ->");

        script = staticHelper(script);
        script = alias(script);
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
        sb.append("public class ").append(name).append(" implements org.apache.camel.language.joor.JoorMethod {\n");
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
                "    public Object evaluate(CamelContext context, Exchange exchange, Message message, Object body, Optional optionalBody) throws Exception {\n");
        sb.append("        ");
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

    private String staticHelper(String script) {
        Matcher matcher = BODY_AS_PATTERN.matcher(script);
        script = matcher.replaceAll("$1(message, $2$3)");
        matcher = BODY_AS_PATTERN_NO_CLASS.matcher(script);
        script = matcher.replaceAll("$1(message, $2.class)");

        matcher = HEADER_AS_DEFAULT_VALUE_PATTERN.matcher(script);
        script = matcher.replaceAll("$1(message, $2, $3, $4)");
        matcher = HEADER_AS_DEFAULT_VALUE_PATTERN_NO_CLASS.matcher(script);
        script = matcher.replaceAll("$1(message, $2, $3, $4.class)");
        matcher = HEADER_AS_PATTERN.matcher(script);
        script = matcher.replaceAll("$1(message, $2, $3)");
        matcher = HEADER_AS_PATTERN_NO_CLASS.matcher(script);
        script = matcher.replaceAll("$1(message, $2, $3.class)");

        matcher = EXCHANGE_PROPERTY_AS_DEFAULT_VALUE_PATTERN.matcher(script);
        script = matcher.replaceAll("$1(exchange, $2, $3, $4)");
        matcher = EXCHANGE_PROPERTY_AS_DEFAULT_VALUE_PATTERN_NO_CLASS.matcher(script);
        script = matcher.replaceAll("$1(exchange, $2, $3, $4.class)");
        matcher = EXCHANGE_PROPERTY_AS_PATTERN.matcher(script);
        script = matcher.replaceAll("$1(exchange, $2, $3)");
        matcher = EXCHANGE_PROPERTY_AS_PATTERN_NO_CLASS.matcher(script);
        script = matcher.replaceAll("$1(exchange, $2, $3.class)");
        return script;
    }

    private String alias(String script) {
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            script = script.replace(key, value);
        }
        return script;
    }

    private static String nextFQN() {
        return "org.apache.camel.language.joor.compiled.JoorScript" + UUID.incrementAndGet();
    }

}
