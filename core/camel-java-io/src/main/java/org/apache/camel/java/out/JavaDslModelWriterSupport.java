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

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.ErrorHandlerDefinition;
import org.apache.camel.model.ExpressionSubElementDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.LoadBalancerDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.config.BatchResequencerConfig;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition;
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
import org.apache.camel.model.language.SingleInputTypedExpressionDefinition;
import org.apache.camel.model.language.TokenizerExpression;
import org.apache.camel.model.language.TypedExpressionDefinition;
import org.apache.camel.model.language.VariableExpression;
import org.apache.camel.model.language.WasmExpression;
import org.apache.camel.model.language.XMLTokenizerExpression;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.model.language.XQueryExpression;
import org.apache.camel.model.loadbalancer.FailoverLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.StickyLoadBalancerDefinition;

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

    private static final Set<String> PREDICATE_CHILDREN = Set.of("handled", "continued", "retryWhile");

    private static final Set<String> SKIP_ATTRIBUTES = Set.of("customId");

    protected int indentLevel = 1;
    protected final Set<String> handledAttributes = new HashSet<>();

    protected void resetState() {
        indentLevel = 1;
        handledAttributes.clear();
    }

    protected void writeRoute(StringBuilder sb, RouteDefinition def) {
        // extract intercepts from outputs — they are RouteBuilder-level in Java DSL
        if (def.getOutputs() != null) {
            for (ProcessorDefinition<?> output : def.getOutputs()) {
                if (output instanceof InterceptFromDefinition ifd) {
                    writeInterceptFrom(sb, ifd);
                } else if (output instanceof InterceptSendToEndpointDefinition iste) {
                    writeInterceptSendToEndpoint(sb, iste);
                } else if (output instanceof InterceptDefinition id) {
                    writeIntercept(sb, id);
                }
            }
        }

        // extract inlined error handler — RouteBuilder-level in Java DSL
        if (def.getErrorHandler() != null) {
            writeErrorHandler(sb, def.getErrorHandler());
            handledAttributes.add("errorHandler");
        }

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
            } else if (value instanceof ExpressionSubElementDefinition esd) {
                if (esd.getExpressionType() != null) {
                    boolean isPredicate = PREDICATE_CHILDREN.contains(key);
                    sb.append(NL).append(indent()).append("    .").append(key).append("(");
                    if (isPredicate) {
                        sb.append("(Predicate) ");
                    }
                    sb.append(expressionDsl(esd.getExpressionType())).append(")");
                }
            } else if (value instanceof BatchResequencerConfig brc) {
                writeBatchResequencerConfig(sb, brc);
            } else if (value instanceof DataFormatDefinition df) {
                writeDataFormatBuilder(sb, key, df, writer);
            } else if (value instanceof LoadBalancerDefinition lb) {
                writeLoadBalancerType(sb, lb);
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
                if (!isInterceptDefinition(item)) {
                    writer.accept(sb, item);
                }
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
        if (list != null && !list.isEmpty() && !handledAttributes.contains(itemKey)) {
            for (String s : list) {
                if (s != null) {
                    sb.append(NL).append(indent()).append("    .").append(itemKey).append("(").append(quote(s)).append(")");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected <T> void writeDataFormatBuilder(
            StringBuilder sb, String key, DataFormatDefinition df, BiConsumer<StringBuilder, T> writer) {
        String str = sb.toString();
        if (str.endsWith(".marshal()") || str.endsWith(".unmarshal()")) {
            sb.setLength(sb.length() - 1);
            sb.append("dataFormat().").append(key).append("()");
            writer.accept(sb, (T) df);
            sb.append(NL).append(indent()).append("    .end())");
        }
    }

    private void writeInterceptFrom(StringBuilder sb, InterceptFromDefinition def) {
        sb.append("interceptFrom(");
        if (def.getUri() != null) {
            sb.append(quote(def.getUri()));
        }
        sb.append(")");
        if (def.getOutputs() != null) {
            for (ProcessorDefinition<?> output : def.getOutputs()) {
                writeInterceptOutput(sb, output);
            }
        }
        sb.append(";").append(NL).append(NL);
    }

    private void writeIntercept(StringBuilder sb, InterceptDefinition def) {
        sb.append("intercept()");
        if (def.getOutputs() != null) {
            for (ProcessorDefinition<?> output : def.getOutputs()) {
                writeInterceptOutput(sb, output);
            }
        }
        sb.append(";").append(NL).append(NL);
    }

    private void writeInterceptSendToEndpoint(StringBuilder sb, InterceptSendToEndpointDefinition def) {
        sb.append("interceptSendToEndpoint(").append(quote(def.getUri())).append(")");
        if (def.getOutputs() != null) {
            for (ProcessorDefinition<?> output : def.getOutputs()) {
                writeInterceptOutput(sb, output);
            }
        }
        sb.append(";").append(NL).append(NL);
    }

    private void writeErrorHandler(StringBuilder sb, ErrorHandlerDefinition errorHandler) {
        if (errorHandler.getErrorHandlerType() instanceof DeadLetterChannelDefinition dlc) {
            sb.append("errorHandler(deadLetterChannel(").append(quote(dlc.getDeadLetterUri())).append(")");
            appendErrorHandlerOptions(sb, dlc);
            sb.append(");").append(NL).append(NL);
        } else if (errorHandler.getErrorHandlerType() instanceof DefaultErrorHandlerDefinition deh) {
            sb.append("errorHandler(defaultErrorHandler()");
            appendErrorHandlerOptions(sb, deh);
            sb.append(");").append(NL).append(NL);
        } else {
            sb.append("errorHandler(noErrorHandler());").append(NL).append(NL);
        }
    }

    private void appendErrorHandlerOptions(StringBuilder sb, DefaultErrorHandlerDefinition def) {
        if (def.getRedeliveryPolicy() != null) {
            var rp = def.getRedeliveryPolicy();
            appendTypedOption(sb, "maximumRedeliveries", rp.getMaximumRedeliveries());
            appendTypedNonDefaultOption(sb, "redeliveryDelay", rp.getRedeliveryDelay(), "1000");
            appendTypedNonDefaultOption(sb, "logStackTrace", rp.getLogStackTrace(), "true");
            appendTypedNonDefaultOption(sb, "logRetryAttempted", rp.getLogRetryAttempted(), "true");
            appendToggleOption(sb, "asyncDelayedRedelivery", rp.getAsyncDelayedRedelivery());
            appendTypedNonDefaultOption(sb, "backOffMultiplier", rp.getBackOffMultiplier(), "2.0");
            appendToggleOption(sb, "useExponentialBackOff", rp.getUseExponentialBackOff());
            appendTypedNonDefaultOption(sb, "maximumRedeliveryDelay", rp.getMaximumRedeliveryDelay(), "60000");
            appendOption(sb, "delayPattern", rp.getDelayPattern());
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void writeInterceptOutput(StringBuilder sb, ProcessorDefinition<?> output) {
        handledAttributes.clear();
        doWriteProcessorDefinitionRef(sb, (ProcessorDefinition) output);
    }

    @SuppressWarnings("rawtypes")
    protected abstract void doWriteProcessorDefinitionRef(StringBuilder sb, ProcessorDefinition v);

    protected boolean isInterceptDefinition(Object def) {
        return def instanceof InterceptDefinition
                || def instanceof InterceptFromDefinition
                || def instanceof InterceptSendToEndpointDefinition;
    }

    protected void writeBatchResequencerConfig(StringBuilder sb, BatchResequencerConfig brc) {
        sb.append(NL).append(indent()).append("    .batch()");
        if (brc.getBatchSize() != null) {
            sb.append(NL).append(indent()).append("    .size(").append(brc.getBatchSize()).append(")");
        }
        if (brc.getBatchTimeout() != null) {
            sb.append(NL).append(indent()).append("    .timeout(").append(brc.getBatchTimeout()).append(")");
        }
    }

    protected void writeLoadBalancerType(StringBuilder sb, LoadBalancerDefinition lb) {
        if (lb instanceof FailoverLoadBalancerDefinition failover) {
            sb.append(".failover(");
            if (failover.getExceptions() != null && !failover.getExceptions().isEmpty()) {
                boolean first = true;
                for (String ex : failover.getExceptions()) {
                    if (!first) {
                        sb.append(", ");
                    }
                    first = false;
                    sb.append(classLiteral(ex));
                }
            }
            sb.append(")");
            handledAttributes.add("exception");
            handledAttributes.add("exceptions");
        } else if (lb instanceof StickyLoadBalancerDefinition sticky) {
            sb.append(".sticky(");
            if (sticky.getCorrelationExpression() != null
                    && sticky.getCorrelationExpression().getExpressionType() != null) {
                sb.append(expressionDsl(sticky.getCorrelationExpression().getExpressionType()));
            }
            sb.append(")");
            handledAttributes.add("correlationExpression");
            handledAttributes.add("expression");
        } else {
            // Derive method name: strip "LoadBalancer" suffix, lowercase first char
            String className = lb.getClass().getSimpleName();
            String methodName = className.replace("LoadBalancerDefinition", "");
            methodName = Character.toLowerCase(methodName.charAt(0)) + methodName.substring(1);
            sb.append(".").append(methodName).append("()");
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

        // check for expression-specific options beyond just the expression text
        String options = expressionBuilderOptions(expr);
        if (!options.isEmpty()) {
            // builder form: expression().langName("text").opt1("val").end()
            String builderMethod = expressionBuilderMethod(expr);
            if (builderMethod != null) {
                return "expression()." + builderMethod + "(" + quote(value) + ")" + options + ".end()";
            }
        }

        // compact form: langName("text") — no options
        return compactExpressionDsl(expr, value);
    }

    private String compactExpressionDsl(ExpressionDefinition expr, String value) {
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
        String lang = expr.getLanguage();
        if (lang != null && !lang.isEmpty()) {
            return "language(" + quote(lang) + ", " + quotedValue + ")";
        }
        return "expression(" + quotedValue + ")";
    }

    private String expressionBuilderMethod(ExpressionDefinition expr) {
        if (expr instanceof SimpleExpression) {
            return "simple";
        }
        if (expr instanceof ConstantExpression) {
            return "constant";
        }
        if (expr instanceof HeaderExpression) {
            return "header";
        }
        if (expr instanceof VariableExpression) {
            return "variable";
        }
        if (expr instanceof XPathExpression) {
            return "xpath";
        }
        if (expr instanceof XQueryExpression) {
            return "xquery";
        }
        if (expr instanceof JsonPathExpression) {
            return "jsonpath";
        }
        if (expr instanceof JqExpression) {
            return "jq";
        }
        if (expr instanceof JoorExpression) {
            return "joor";
        }
        if (expr instanceof CSimpleExpression) {
            return "csimple";
        }
        if (expr instanceof DatasonnetExpression) {
            return "datasonnet";
        }
        if (expr instanceof TokenizerExpression) {
            return "tokenize";
        }
        if (expr instanceof XMLTokenizerExpression) {
            return "xtokenize";
        }
        if (expr instanceof MethodCallExpression) {
            return "bean";
        }
        if (expr instanceof RefExpression) {
            return "ref";
        }
        if (expr instanceof WasmExpression) {
            return "wasm";
        }
        String lang = expr.getLanguage();
        if (lang != null && !lang.isEmpty()) {
            return "language";
        }
        return null;
    }

    private String expressionBuilderOptions(ExpressionDefinition expr) {
        StringBuilder opts = new StringBuilder();

        // common: resultType (on TypedExpressionDefinition)
        if (expr instanceof TypedExpressionDefinition typed) {
            appendOption(opts, "resultTypeName", typed.getResultTypeName());
        }
        // common: source (on SingleInputTypedExpressionDefinition)
        if (expr instanceof SingleInputTypedExpressionDefinition single) {
            appendOption(opts, "source", single.getSource());
        }

        // type-specific options
        if (expr instanceof SimpleExpression se) {
            appendNonDefaultOption(opts, "trimResult", se.getTrimResult(), "false");
            appendNonDefaultOption(opts, "pretty", se.getPretty(), "false");
            appendNonDefaultOption(opts, "nested", se.getNested(), "false");
        } else if (expr instanceof JsonPathExpression jp) {
            appendNonDefaultOption(opts, "suppressExceptions", jp.getSuppressExceptions(), "false");
            appendNonDefaultOption(opts, "allowSimple", jp.getAllowSimple(), "true");
            appendNonDefaultOption(opts, "allowEasyPredicate", jp.getAllowEasyPredicate(), "true");
            appendNonDefaultOption(opts, "writeAsString", jp.getWriteAsString(), "false");
            appendNonDefaultOption(opts, "unpackArray", jp.getUnpackArray(), "false");
            appendOption(opts, "option", jp.getOption());
        } else if (expr instanceof XPathExpression xp) {
            appendOption(opts, "documentTypeName", xp.getDocumentTypeName());
            appendNonDefaultOption(opts, "resultQName", xp.getResultQName(), "NODESET");
            appendOption(opts, "saxon", xp.getSaxon());
            appendOption(opts, "factoryRef", xp.getFactoryRef());
            appendOption(opts, "objectModel", xp.getObjectModel());
            appendOption(opts, "logNamespaces", xp.getLogNamespaces());
            appendOption(opts, "threadSafety", xp.getThreadSafety());
            appendNonDefaultOption(opts, "preCompile", xp.getPreCompile(), "true");
        } else if (expr instanceof TokenizerExpression te) {
            appendOption(opts, "endToken", te.getEndToken());
            appendOption(opts, "inheritNamespaceTagName", te.getInheritNamespaceTagName());
            appendOption(opts, "regex", te.getRegex());
            appendOption(opts, "xml", te.getXml());
            appendOption(opts, "includeTokens", te.getIncludeTokens());
            appendOption(opts, "group", te.getGroup());
            appendOption(opts, "groupDelimiter", te.getGroupDelimiter());
            appendOption(opts, "skipFirst", te.getSkipFirst());
        } else if (expr instanceof MethodCallExpression mc) {
            appendOption(opts, "beanTypeName", mc.getBeanTypeName());
            appendNonDefaultOption(opts, "scope", mc.getScope(), "Singleton");
            appendNonDefaultOption(opts, "validate", mc.getValidate(), "true");
        } else if (expr instanceof XQueryExpression xq) {
            appendOption(opts, "configurationRef", xq.getConfigurationRef());
        } else if (expr instanceof XMLTokenizerExpression xt) {
            appendNonDefaultOption(opts, "mode", xt.getMode(), "i");
            appendOption(opts, "group", xt.getGroup());
        } else if (expr instanceof WasmExpression w) {
            appendOption(opts, "module", w.getModule());
        }

        return opts.toString();
    }

    private void appendOption(StringBuilder sb, String name, String value) {
        if (value != null && !value.isEmpty()) {
            sb.append(".").append(name).append("(").append(quote(value)).append(")");
        }
    }

    private void appendNonDefaultOption(StringBuilder sb, String name, String value, String defaultValue) {
        if (value != null && !value.isEmpty() && !value.equals(defaultValue)) {
            sb.append(".").append(name).append("(").append(quote(value)).append(")");
        }
    }

    private void appendTypedOption(StringBuilder sb, String name, String value) {
        if (value != null && !value.isEmpty()) {
            sb.append(".").append(name).append("(").append(value).append(")");
        }
    }

    private void appendTypedNonDefaultOption(StringBuilder sb, String name, String value, String defaultValue) {
        if (value != null && !value.isEmpty() && !value.equals(defaultValue)) {
            sb.append(".").append(name).append("(").append(value).append(")");
        }
    }

    private void appendToggleOption(StringBuilder sb, String name, String value) {
        if ("true".equals(value)) {
            sb.append(".").append(name).append("()");
        }
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
        // java.lang classes use simple name (no import needed), others use FQN
        if (typeName.startsWith("java.lang.")) {
            return typeName.substring("java.lang.".length()) + ".class";
        }
        return typeName + ".class";
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
