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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.util.StringHelper;

/**
 * Source code generate for csimple language.
 *
 * @see CSimpleGeneratedCode
 */
public class CSimpleCodeGenerator implements CamelContextAware {

    private static final AtomicInteger UUID = new AtomicInteger();

    private CamelContext camelContext;
    private Set<String> imports = new TreeSet<>();
    private Map<String, String> aliases = new HashMap<>();

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

    public CSimpleGeneratedCode generateExpression(String fqn, String script) {
        return generateCode(fqn, script, false);
    }

    public CSimpleGeneratedCode generatePredicate(String fqn, String script) {
        return generateCode(fqn, script, true);
    }

    private CSimpleGeneratedCode generateCode(String fqn, String script, boolean predicate) {
        String text = script;
        // text should be single line and trimmed as it can be multi-lined
        text = text.replaceAll("\n", "");
        text = text.trim();

        String qn = fqn.substring(0, fqn.lastIndexOf('.'));
        String name = nextName();

        // trim text
        script = script.trim();
        script = alias(script);

        //  wrap text into a class method we can call
        StringBuilder sb = new StringBuilder(4096);
        sb.append("package ").append(qn).append(";\n");
        sb.append("\n");
        sb.append("import java.util.*;\n");
        sb.append("import java.util.concurrent.*;\n");
        sb.append("import java.util.stream.*;\n");
        sb.append("\n");
        sb.append("import org.apache.camel.*;\n");
        sb.append("import org.apache.camel.util.*;\n");
        sb.append("import org.apache.camel.spi.*;\n");
        sb.append("import static org.apache.camel.language.csimple.CSimpleHelper.*;\n");
        sb.append("\n");
        // custom imports
        for (String i : imports) {
            sb.append(i);
            if (!i.endsWith(";")) {
                sb.append(";");
            }
            sb.append("\n");
        }
        sb.append("\n");
        sb.append("public class ").append(name).append(" extends org.apache.camel.language.csimple.CSimpleSupport {\n");
        sb.append("\n");
        sb.append("    Language bean;\n");
        sb.append("    UuidGenerator uuid;\n");
        sb.append("\n");
        sb.append("    public ").append(name).append("() {\n");
        sb.append("    }\n");
        sb.append("\n");

        sb.append("    @Override\n");
        sb.append("    public boolean isPredicate() {\n");
        sb.append("        return ").append(predicate).append(";\n");
        sb.append("    }\n");
        sb.append("\n");

        sb.append("    @Override\n");
        sb.append("    public String getText() {\n");
        // \ should be escaped
        String escaped = text.replace("\\", "\\\\");
        // we need to escape all " so it's a single literal string
        escaped = escaped.replace("\"", "\\\"");
        sb.append("        return \"").append(escaped).append("\";\n");
        sb.append("    }\n");
        sb.append("\n");

        sb.append("    @Override\n");
        sb.append(
                "    public Object evaluate(CamelContext context, Exchange exchange, Message message, Object body) throws Exception {\n");
        sb.append("        ");

        if (predicate) {
            CSimplePredicateParser parser = new CSimplePredicateParser();
            script = parser.parsePredicate(camelContext, script);
            if (script.isBlank()) {
                // a predicate that is whitespace is regarded as false
                script = "false";
            }
        } else {
            CSimpleExpressionParser parser = new CSimpleExpressionParser();
            script = parser.parseExpression(camelContext, script);
            if (script.isBlank()) {
                // an expression can be whitespace, but then we need to wrap this in quotes
                script = "\"" + script + "\"";
            }
        }
        String uuid = null;
        if (script.startsWith("    UuidGenerator uuid")) {
            uuid = StringHelper.before(script, "\n") + "\n";
            script = StringHelper.after(script, "\n");
        }

        if (!script.contains("return ")) {
            sb.append("return ");
        }
        sb.append(script);
        if (!script.endsWith("}") && !script.endsWith(";")) {
            sb.append(";");
        }
        sb.append("\n");
        sb.append("    }\n");

        // only resolve bean language if we use it as camel-bean must then be on the classpath
        if (script.contains("bean(exchange, bean,")) {
            sb.append("\n");
            sb.append("    @Override\n");
            sb.append("    public void init(CamelContext context) {\n");
            sb.append("        bean = context.resolveLanguage(\"bean\");\n");
            sb.append("    }\n");
            sb.append("\n");
        }

        sb.append("}\n");
        sb.append("\n");

        String code = sb.toString();
        if (uuid != null) {
            code = code.replace("    UuidGenerator uuid;\n", uuid);
        }

        return new CSimpleGeneratedCode(qn + "." + name, code);
    }

    private String alias(String script) {
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            script = script.replace(key, value);
        }
        return script;
    }

    private static String nextName() {
        return "CSimpleScript" + UUID.incrementAndGet();
    }

}
