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
package org.apache.camel.java.out;

import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.language.CSimpleExpression;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.DatasonnetExpression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.model.language.JoorExpression;
import org.apache.camel.model.language.JqExpression;
import org.apache.camel.model.language.JsonPathExpression;
import org.apache.camel.model.language.MethodCallExpression;
import org.apache.camel.model.language.RefExpression;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.model.language.TokenizerExpression;
import org.apache.camel.model.language.VariableExpression;
import org.apache.camel.model.language.XMLTokenizerExpression;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.model.language.XQueryExpression;

/**
 * Base class for the generated {@link JavaDslModelWriter}. Provides helper methods for building Java DSL source code
 * strings from Camel model definitions.
 */
public abstract class JavaDslModelWriterSupport {

    private static final String NL = "\n";

    private static final Set<String> BLOCK_EIPS = Set.of(
            "choice", "filter", "split", "multicast", "pipeline",
            "doTry", "circuitBreaker", "step", "saga", "loop",
            "transacted", "aggregate", "resequence", "idempotentConsumer",
            "onCompletion", "loadBalance", "kamelet", "onException",
            "intercept", "interceptFrom", "interceptSendToEndpoint");

    private static final Set<String> BLOCK_CHILDREN = Set.of(
            "otherwise", "doFinally", "onFallback");

    private static final Set<String> SKIP_ATTRIBUTES = Set.of("customId");

    protected int indentLevel = 1;
    protected final Set<String> handledAttributes = new HashSet<>();

    protected void resetState() {
        indentLevel = 1;
        handledAttributes.clear();
    }

    protected void writeRoute(StringBuilder sb, RouteDefinition def) {
        if (def.getInput() != null) {
            sb.append("from(").append(quote(def.getInput().getUri())).append(")");
        }
        handledAttributes.add("input");
        handledAttributes.add("uri");
        if (def.getId() != null) {
            sb.append(NL).append(indent()).append(".routeId(").append(quote(def.getId())).append(")");
            handledAttributes.add("id");
        }
        handledAttributes.add("customId");
        if (def.getRouteProperties() != null) {
            for (PropertyDefinition prop : def.getRouteProperties()) {
                sb.append(NL).append(indent()).append(".routeProperty(")
                        .append(quote(prop.getKey())).append(", ")
                        .append(quote(prop.getValue())).append(")");
            }
            handledAttributes.add("routeProperty");
            handledAttributes.add("routeProperties");
        }
        indentLevel--;
        doWriteRouteDefinition(sb, def);
        indentLevel++;
        sb.append(";");
    }

    protected abstract void doWriteRouteDefinition(StringBuilder sb, RouteDefinition def);

    /**
     * Begins a DSL step method call. This is the fallback for EIPs that don't have Jandex-derived primary args
     * generated in the dispatch code.
     */
    protected void beginStep(StringBuilder sb, String name, Object def) {
        handledAttributes.clear();
        sb.append(NL).append(indent()).append(".").append(name).append("()");
    }

    /**
     * Ends a DSL step. For block EIPs that have outputs, appends .end().
     */
    protected void endStep(StringBuilder sb, String name, Object def) {
        if (BLOCK_EIPS.contains(name)) {
            sb.append(NL).append(indent()).append(".end()");
        }
    }

    protected void doWriteAttribute(StringBuilder sb, String key, String value, String defaultValue) {
        if (value == null || handledAttributes.contains(key) || SKIP_ATTRIBUTES.contains(key)) {
            return;
        }
        if (defaultValue != null && defaultValue.equals(value)) {
            return;
        }
        sb.append(NL).append(indent()).append("    .").append(key).append("(").append(quote(value)).append(")");
    }

    protected void doWriteValue(StringBuilder sb, String value) {
        // @XmlValue - not typically used in Java DSL
    }

    protected void doWriteExpressionRef(StringBuilder sb, ExpressionDefinition expr) {
        if (expr != null && !handledAttributes.contains("expression")) {
            sb.append(NL).append(indent()).append("    .").append(expressionDsl(expr));
        }
    }

    protected <T> void doWriteChildElement(StringBuilder sb, String key, T value, BiConsumer<StringBuilder, T> writer) {
        if (value != null && !handledAttributes.contains(key)) {
            if (BLOCK_CHILDREN.contains(key)) {
                indentLevel++;
                sb.append(NL).append(indent()).append(".").append(key).append("()");
                writer.accept(sb, value);
                indentLevel--;
            } else {
                writer.accept(sb, value);
            }
        }
    }

    protected <T> void doWriteElementRef(StringBuilder sb, String key, T value, BiConsumer<StringBuilder, T> writer) {
        if (value != null && !handledAttributes.contains(key)) {
            writer.accept(sb, value);
        }
    }

    protected <T> void doWriteOutputs(
            StringBuilder sb, List<T> list, BiConsumer<StringBuilder, T> writer) {
        if (list != null && !list.isEmpty()) {
            indentLevel++;
            for (T item : list) {
                writer.accept(sb, item);
            }
            indentLevel--;
        }
    }

    protected <T> void doWriteChildList(
            StringBuilder sb, String key, List<T> list, BiConsumer<StringBuilder, T> writer) {
        if (list != null && !list.isEmpty() && !handledAttributes.contains(key)) {
            indentLevel++;
            for (T item : list) {
                writer.accept(sb, item);
            }
            indentLevel--;
        }
    }

    @SuppressWarnings("unchecked")
    protected void doWriteStringList(StringBuilder sb, String wrapperKey, String itemKey, List<String> list) {
        // String lists are typically rendered as multiple method calls or comma-separated args
        if (list != null && !list.isEmpty()) {
            for (String s : list) {
                if (s != null) {
                    sb.append(NL).append(indent()).append("    .").append(itemKey).append("(").append(quote(s)).append(")");
                }
            }
        }
    }

    protected String expressionDsl(ExpressionDefinition expr) {
        if (expr == null) {
            return "";
        }
        String value = expr.getExpression();
        if (value == null) {
            value = "";
        }
        String quotedValue = quote(value);

        if (expr instanceof SimpleExpression) {
            return "simple(" + quotedValue + ")";
        }
        if (expr instanceof ConstantExpression) {
            return "constant(" + quotedValue + ")";
        }
        if (expr instanceof HeaderExpression) {
            return "header(" + quotedValue + ")";
        }
        if (expr instanceof VariableExpression) {
            return "variable(" + quotedValue + ")";
        }
        if (expr instanceof XPathExpression) {
            return "xpath(" + quotedValue + ")";
        }
        if (expr instanceof XQueryExpression) {
            return "xquery(" + quotedValue + ")";
        }
        if (expr instanceof JsonPathExpression) {
            return "jsonpath(" + quotedValue + ")";
        }
        if (expr instanceof JqExpression) {
            return "jq(" + quotedValue + ")";
        }
        if (expr instanceof JoorExpression) {
            return "joor(" + quotedValue + ")";
        }
        if (expr instanceof CSimpleExpression) {
            return "csimple(" + quotedValue + ")";
        }
        if (expr instanceof DatasonnetExpression) {
            return "datasonnet(" + quotedValue + ")";
        }
        if (expr instanceof TokenizerExpression) {
            return "tokenize(" + quotedValue + ")";
        }
        if (expr instanceof XMLTokenizerExpression) {
            return "xtokenize(" + quotedValue + ")";
        }
        if (expr instanceof MethodCallExpression mc) {
            if (mc.getRef() != null) {
                return "method(" + quote(mc.getRef()) + (mc.getMethod() != null ? ", " + quote(mc.getMethod()) : "") + ")";
            }
            return "method(" + quotedValue + ")";
        }
        if (expr instanceof RefExpression) {
            return "ref(" + quotedValue + ")";
        }
        // languages without RouteBuilder helpers use the generic language() factory
        String lang = expr.getLanguage();
        if (lang != null && !lang.isEmpty()) {
            return "language(" + quote(lang) + ", " + quotedValue + ")";
        }
        return "expression(" + quotedValue + ")";
    }

    protected String indent() {
        return "    ".repeat(indentLevel);
    }

    protected String quote(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    protected String classLiteral(String typeName) {
        if (typeName == null) {
            return "Object.class";
        }
        String simple = typeName.contains(".")
                ? typeName.substring(typeName.lastIndexOf('.') + 1)
                : typeName;
        return simple + ".class";
    }

    protected String enumLiteral(Enum<?> e) {
        if (e == null) {
            return "null";
        }
        return e.getClass().getSimpleName() + "." + e.name();
    }

    protected String toString(Boolean b) {
        return b != null ? b.toString() : null;
    }

    protected String toString(Enum<?> e) {
        return e != null ? e.name() : null;
    }

    protected String toString(Number n) {
        return n != null ? n.toString() : null;
    }

    protected String toString(byte[] b) {
        return b != null ? Base64.getEncoder().encodeToString(b) : null;
    }
}
