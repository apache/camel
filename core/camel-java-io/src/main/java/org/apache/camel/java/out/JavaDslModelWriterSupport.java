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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.BiConsumer;

import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.builder.LanguageBuilderFactory;
import org.apache.camel.model.BeanDefinition;
import org.apache.camel.model.BeanFactoryDefinition;
import org.apache.camel.model.ConvertBodyDefinition;
import org.apache.camel.model.ConvertHeaderDefinition;
import org.apache.camel.model.ConvertVariableDefinition;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.model.ErrorHandlerDefinition;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.ExpressionSubElementDefinition;
import org.apache.camel.model.InputTypeDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.LoadBalancerDefinition;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.LoopDefinition;
import org.apache.camel.model.OnWhenDefinition;
import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.model.OutputTypeDefinition;
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.model.PropertyExpressionDefinition;
import org.apache.camel.model.RemoveHeadersDefinition;
import org.apache.camel.model.RemovePropertiesDefinition;
import org.apache.camel.model.RollbackDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplateParameterDefinition;
import org.apache.camel.model.SamplingDefinition;
import org.apache.camel.model.SetHeaderDefinition;
import org.apache.camel.model.SetHeadersDefinition;
import org.apache.camel.model.SetVariableDefinition;
import org.apache.camel.model.SetVariablesDefinition;
import org.apache.camel.model.SwitchCaseDefinition;
import org.apache.camel.model.SwitchDefinition;
import org.apache.camel.model.TemplatedRouteDefinition;
import org.apache.camel.model.TemplatedRouteParameterDefinition;
import org.apache.camel.model.ThrowExceptionDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.model.ValueDefinition;
import org.apache.camel.model.config.BatchResequencerConfig;
import org.apache.camel.model.config.StreamResequencerConfig;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.DatasonnetExpression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.model.language.JqExpression;
import org.apache.camel.model.language.JsonPathExpression;
import org.apache.camel.model.language.LanguageExpression;
import org.apache.camel.model.language.MethodCallExpression;
import org.apache.camel.model.language.NamespaceAwareExpression;
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
import org.apache.camel.model.loadbalancer.CustomLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.FailoverLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.StickyLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.WeightedLoadBalancerDefinition;
import org.apache.camel.model.rest.ResponseHeaderDefinition;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestPropertyDefinition;
import org.apache.camel.model.tokenizer.LangChain4jTokenizerDefinition;
import org.apache.camel.model.transformer.CustomTransformerDefinition;
import org.apache.camel.model.transformer.EndpointTransformerDefinition;
import org.apache.camel.model.transformer.LoadTransformerDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.validator.CustomValidatorDefinition;
import org.apache.camel.model.validator.EndpointValidatorDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.support.builder.ValueBuilder;
import org.apache.camel.util.TimeUtils;

/**
 * Base class for the generated {@link JavaDslModelWriter}. Provides helper methods for building Java DSL source code
 * strings from Camel model definitions.
 * <p>
 * <b>Not thread-safe.</b> Instances use mutable state (indentLevel, handledAttributes) during writing and must not be
 * shared across threads. Create one instance per thread or per conversion.
 */
public abstract class JavaDslModelWriterSupport {

    protected void writeSwitch(StringBuilder sb, SwitchDefinition definition) {
        definition.preCreateProcessor();
        handledAttributes.clear();
        sb.append(NL).append(indent()).append(".doSwitch(")
                .append(expressionDsl(definition.getSelector().getExpressionType())).append(")");
        doWriteProcessorDefinitionAttributes(sb, definition);
        for (SwitchCaseDefinition c : definition.getCases()) {
            sb.append(NL).append(indent()).append(".doCase(");
            sb.append(quote(c.getValue()));
            sb.append(")");
            handledAttributes.clear();
            doWriteOptionalIdentifiedDefinitionAttributes(sb, c);
            sb.append(".to(").append(quote(c.getUri())).append(")");
        }
        if (definition.getOtherwise() != null) {
            sb.append(NL).append(indent()).append(".otherwise(").append(quote(definition.getOtherwise().getUri())).append(")");
        }
        sb.append(NL).append(indent()).append(".end()");
    }

    protected abstract void doWriteProcessorDefinitionAttributes(StringBuilder sb, ProcessorDefinition<?> definition);

    protected abstract void doWriteOptionalIdentifiedDefinitionAttributes(
            StringBuilder sb, OptionalIdentifiedDefinition<?> definition);

    private static final String NL = "\n";

    private static final Set<String> BLOCK_EIPS = Set.of(
            "choice", "filter", "split", "multicast", "pipeline",
            "doTry", "circuitBreaker", "step", "saga", "loop",
            "transacted", "aggregate", "resequence", "idempotentConsumer",
            "onCompletion", "loadBalance", "kamelet", "onException",
            "intercept", "interceptFrom", "interceptSendToEndpoint", "policy");

    private static final Set<String> BLOCK_CHILDREN = Set.of(
            "otherwise", "doFinally", "onFallback");

    private static final Set<String> PREDICATE_CHILDREN = Set.of("handled", "continued", "retryWhile");

    /** Expression options whose fluent method has another name: aggregate's completionSize(Expression). */
    private static final Map<String, String> CHILD_RENAMES = Map.of(
            "completionSizeExpression", "completionSize", "completionTimeoutExpression", "completionTimeout");

    private static final Set<String> SKIP_ATTRIBUTES = Set.of("customId");

    // Builder methods that are no-arg toggles: .name() when "true", skip when "false"
    private static final Set<String> TOGGLE_ATTRIBUTES = Set.of(
            "aggregationStrategyMethodAllowNull", "completeAllOnStop", "completionFromBatchConsumer",
            "completionOnNewCorrelationGroup", "copy", "deprecated",
            "discardOnAggregationFailure", "discardOnCompletionTimeout",
            "eagerCheckCompletion", "forceCompletionOnStop", "ignoreInvalidCorrelationKeys",
            "ignoreInvalidEndpoint", "breakOnShutdown",
            "onCompleteOnly", "onFailureOnly",
            "optimisticLocking", "optimisticLockingSyncRetry",
            "shareUnitOfWork", "skipSendToOriginalEndpoint", "streaming",
            "useCollisionAvoidance", "useExponentialBackOff", "asyncDelayedRedelivery",
            "useOriginalBody", "useOriginalMessage");

    // Builder methods that take boolean (not String): .name(true/false) unquoted
    private static final Set<String> BOOLEAN_ATTRIBUTES = Set.of(
            "aggregateOnException", "allowNullBody",
            "clientRequestValidation", "clientResponseValidation",
            "completionEager", "deadLetterHandleNewException",
            "eager", "enableCORS", "enableNoContentResponse",
            "failIfNoTaskContext",
            "inheritErrorHandler", "intermittent",
            "parallelProcessing", "precondition",
            "removeOnFailure", "skipBindingOnErrorCode", "skipDuplicate",
            "validate");

    // Child elements rendered as sub-builder: .name().option("val").end()
    private static final Set<String> SUB_BUILDER_CHILDREN = Set.of(
            "resilience4jConfiguration", "faultToleranceConfiguration", "openApi");

    // REST DSL sub-builder steps with custom end methods
    private static final Map<String, String> REST_END_METHODS = Map.of(
            "param", "endParam",
            "responseMessage", "endResponseMessage");

    // Attributes rendered as class literals: .name(com.example.Foo.class)
    private static final Set<String> CLASS_LITERAL_ATTRIBUTES = Set.of("responseModel");

    // Data format builder methods where the String setter name differs from the XML attribute name.
    // The XML attribute is the Class<?> setter; the String setter appends "Name" or "AsString".
    private static final Map<String, String> DATAFORMAT_ATTR_RENAMES = Map.of(
            "unmarshalType", "unmarshalTypeName",
            "collectionType", "collectionTypeName",
            "jsonView", "jsonViewTypeName");

    private boolean inDataFormatBuilder;
    private boolean inSubBuilder;
    /** The definition whose options are being written, whose fluent methods say how to write each one. */
    private Object optionOwner;
    /** Where the call of the step being written starts, for a step whose options must be its arguments. */
    private int stepStart = -1;
    /** The onFallback() of the circuit breaker being written, written last. */
    private Runnable pendingFallback;
    private boolean inRestParam;
    private boolean generatedIds;
    private boolean sourceLocation;
    private boolean currentNodeCustomId;

    protected int indentLevel = 1;
    protected final Set<String> handledAttributes = new HashSet<>();

    public void setGeneratedIds(boolean generatedIds) {
        this.generatedIds = generatedIds;
    }

    public void setSourceLocation(boolean sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    public boolean isSourceLocation() {
        return sourceLocation;
    }

    protected void resetState() {
        indentLevel = 1;
        handledAttributes.clear();
        inDataFormatBuilder = false;
        inSubBuilder = false;
        inRestParam = false;
        optionOwner = null;
        pendingFallback = null;
    }

    protected void writeRoute(StringBuilder sb, RouteDefinition def) {
        optionOwner = def;
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

        if (def.getInput() != null) {
            sb.append("from(").append(quote(def.getInput().getUri())).append(")");
            if (sourceLocation) {
                int line = def.getInput().getLineNumber();
                if (line != -1) {
                    sb.append(NL).append("// sourceLineNumber: ").append(line);
                }
            }
        }
        handledAttributes.add("input");
        handledAttributes.add("uri");
        if (def.getId() != null && (generatedIds || Boolean.TRUE.equals(def.getCustomId()))) {
            sb.append(NL).append(indent()).append(".routeId(").append(quote(def.getId())).append(")");
        }
        handledAttributes.add("id");
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
        // the error handler of the route only: errorHandler() of the route builder would apply to all its routes
        if (def.getErrorHandler() != null) {
            sb.append(NL).append(indent()).append(".errorHandler(").append(errorHandlerDsl(def.getErrorHandler()))
                    .append(")");
        }
        handledAttributes.add("errorHandler");
        indentLevel--;
        doWriteRouteDefinition(sb, def);
        indentLevel++;
        if (sourceLocation) {
            sb.append(NL);
        }
        sb.append(";");
    }

    protected abstract void doWriteRouteDefinition(StringBuilder sb, RouteDefinition def);

    protected abstract void doWriteRestDefinition(StringBuilder sb, RestDefinition def);

    public String writeRest(RestDefinition def) {
        resetState();
        StringBuilder sb = new StringBuilder();
        sb.append("rest(");
        if (def.getPath() != null) {
            sb.append(quote(def.getPath()));
        }
        sb.append(")");
        handledAttributes.add("path");
        doWriteRestDefinition(sb, def);
        sb.append(";");
        return sb.toString();
    }

    protected abstract void doWriteTemplatedRouteDefinition(StringBuilder sb, TemplatedRouteDefinition def);

    public String writeRouteTemplate(RouteTemplateDefinition def) {
        resetState();
        StringBuilder sb = new StringBuilder();
        sb.append("routeTemplate(").append(quote(def.getId())).append(")");
        handledAttributes.add("id");
        handledAttributes.add("customId");

        // template parameters — rendered manually since Java DSL uses positional args
        if (def.getTemplateParameters() != null) {
            for (RouteTemplateParameterDefinition param : def.getTemplateParameters()) {
                writeTemplateParameter(sb, param);
            }
        }
        handledAttributes.add("templateParameter");
        handledAttributes.add("templateParameters");

        // template beans — rendered as sub-builders
        if (def.getTemplateBeans() != null && !def.getTemplateBeans().isEmpty()) {
            writeBeanDefinitions(sb, def.getTemplateBeans());
        }
        handledAttributes.add("templateBean");
        handledAttributes.add("templateBeans");

        // embedded route — write from and route body inline
        RouteDefinition route = def.getRoute();
        if (route != null) {
            handledAttributes.add("route");
            if (route.getInput() != null) {
                sb.append(NL).append(indent()).append(".from(").append(quote(route.getInput().getUri())).append(")");
            }
            handledAttributes.add("input");
            handledAttributes.add("uri");
            if (route.getRouteProperties() != null) {
                for (PropertyDefinition prop : route.getRouteProperties()) {
                    sb.append(NL).append(indent()).append(".routeProperty(")
                            .append(quote(prop.getKey())).append(", ")
                            .append(quote(prop.getValue())).append(")");
                }
            }
            handledAttributes.add("routeProperty");
            handledAttributes.add("routeProperties");
            if (route.getErrorHandler() != null) {
                sb.append(NL).append(indent()).append(".errorHandler(")
                        .append(errorHandlerDsl(route.getErrorHandler())).append(")");
            }
            handledAttributes.add("errorHandler");
            indentLevel--;
            doWriteRouteDefinition(sb, route);
            indentLevel++;
        }

        sb.append(";");
        return sb.toString();
    }

    public String writeTemplatedRoute(TemplatedRouteDefinition def) {
        resetState();
        StringBuilder sb = new StringBuilder();
        sb.append("templatedRoute(").append(quote(def.getRouteTemplateRef())).append(")");
        handledAttributes.add("routeTemplateRef");
        doWriteTemplatedRouteDefinition(sb, def);
        sb.append(";");
        return sb.toString();
    }

    protected abstract void doWriteRestConfigurationDefinition(StringBuilder sb, RestConfigurationDefinition def);

    public String writeRestConfiguration(RestConfigurationDefinition def) {
        resetState();
        StringBuilder sb = new StringBuilder();
        sb.append("restConfiguration()");
        doWriteRestConfigurationDefinition(sb, def);
        sb.append(";");
        return sb.toString();
    }

    protected abstract void doWriteRouteConfigurationDefinition(StringBuilder sb, RouteConfigurationDefinition def);

    public String writeRouteConfiguration(RouteConfigurationDefinition def) {
        resetState();
        StringBuilder sb = new StringBuilder();
        sb.append("routeConfiguration(");
        if (def.getId() != null) {
            sb.append(quote(def.getId()));
        }
        sb.append(")");
        handledAttributes.add("id");
        handledAttributes.add("customId");

        // error handler — rendered as chained .errorHandler(deadLetterChannel("uri")...)
        if (def.getErrorHandler() != null) {
            writeChainedErrorHandler(sb, def.getErrorHandler());
            handledAttributes.add("errorHandler");
        }

        doWriteRouteConfigurationDefinition(sb, def);
        sb.append(";");
        return sb.toString();
    }

    public String writeTransformer(TransformerDefinition def) {
        resetState();
        StringBuilder sb = new StringBuilder();
        sb.append("transformer()");
        writeTransformerAttributes(sb, def);
        if (def instanceof EndpointTransformerDefinition etd) {
            if (etd.getUri() != null) {
                sb.append(NL).append(indent()).append("    .withUri(").append(quote(etd.getUri())).append(")");
            }
        } else if (def instanceof CustomTransformerDefinition ctd) {
            if (ctd.getRef() != null) {
                sb.append(NL).append(indent()).append("    .withBean(").append(quote(ctd.getRef())).append(")");
            } else if (ctd.getClassName() != null) {
                sb.append(NL).append(indent()).append("    .withJava(").append(ctd.getClassName()).append(".class)");
            }
        } else if (def instanceof LoadTransformerDefinition ltd) {
            if ("true".equals(ltd.getDefaults())) {
                sb.append(NL).append(indent()).append("    .withDefaults()");
            } else if (ltd.getPackageScan() != null) {
                sb.append(NL).append(indent()).append("    .scan(").append(quote(ltd.getPackageScan())).append(")");
            }
        }
        sb.append(";");
        return sb.toString();
    }

    public String writeValidator(ValidatorDefinition def) {
        resetState();
        StringBuilder sb = new StringBuilder();
        sb.append("validator()");
        if (def.getType() != null) {
            sb.append(NL).append(indent()).append("    .type(").append(quote(def.getType())).append(")");
        }
        if (def instanceof EndpointValidatorDefinition evd) {
            if (evd.getUri() != null) {
                sb.append(NL).append(indent()).append("    .withUri(").append(quote(evd.getUri())).append(")");
            }
        } else if (def instanceof CustomValidatorDefinition cvd) {
            if (cvd.getRef() != null) {
                sb.append(NL).append(indent()).append("    .withBean(").append(quote(cvd.getRef())).append(")");
            } else if (cvd.getClassName() != null) {
                sb.append(NL).append(indent()).append("    .withJava(").append(cvd.getClassName()).append(".class)");
            }
        }
        sb.append(";");
        return sb.toString();
    }

    private void writeTransformerAttributes(StringBuilder sb, TransformerDefinition def) {
        if (def.getScheme() != null) {
            sb.append(NL).append(indent()).append("    .scheme(").append(quote(def.getScheme())).append(")");
        }
        if (def.getName() != null) {
            sb.append(NL).append(indent()).append("    .name(").append(quote(def.getName())).append(")");
        }
        if (def.getFromType() != null) {
            sb.append(NL).append(indent()).append("    .fromType(").append(quote(def.getFromType())).append(")");
        }
        if (def.getToType() != null) {
            sb.append(NL).append(indent()).append("    .toType(").append(quote(def.getToType())).append(")");
        }
    }

    private void writeChainedErrorHandler(StringBuilder sb, ErrorHandlerDefinition errorHandler) {
        sb.append(NL).append(indent()).append("    .errorHandler(").append(errorHandlerDsl(errorHandler)).append(")");
    }

    private void writeTemplateParameter(StringBuilder sb, RouteTemplateParameterDefinition param) {
        boolean hasDefault = param.getDefaultValue() != null;
        boolean hasDescription = param.getDescription() != null;
        boolean isOptional = "false".equalsIgnoreCase(param.getRequired());

        if (isOptional && !hasDefault) {
            sb.append(NL).append(indent()).append(".templateOptionalParameter(").append(quote(param.getName()));
            if (hasDescription) {
                sb.append(", ").append(quote(param.getDescription()));
            }
            sb.append(")");
        } else if (hasDefault || hasDescription) {
            sb.append(NL).append(indent()).append(".templateParameter(").append(quote(param.getName()));
            sb.append(", ").append(quote(param.getDefaultValue()));
            if (hasDescription) {
                sb.append(", ").append(quote(param.getDescription()));
            }
            sb.append(")");
        } else {
            sb.append(NL).append(indent()).append(".templateParameter(").append(quote(param.getName())).append(")");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void writeBeanDefinitions(StringBuilder sb, List<T> list) {
        for (T item : list) {
            BeanFactoryDefinition<?> bean = (BeanFactoryDefinition<?>) item;
            sb.append(NL).append(indent()).append("    .bean(").append(quote(bean.getName())).append(")");
            if (bean.getType() != null) {
                sb.append(NL).append(indent()).append("        .type(").append(quote(bean.getType())).append(")");
            }
            if (bean.getScriptLanguage() != null) {
                sb.append(NL).append(indent()).append("        .scriptLanguage(")
                        .append(quote(bean.getScriptLanguage())).append(")");
            }
            if (bean.getScript() != null) {
                sb.append(NL).append(indent()).append("        .script(").append(quote(bean.getScript())).append(")");
            }
            if (bean.getProperties() != null) {
                for (Map.Entry<String, Object> entry : bean.getProperties().entrySet()) {
                    sb.append(NL).append(indent()).append("        .property(")
                            .append(quote(entry.getKey())).append(", ")
                            .append(quote(String.valueOf(entry.getValue()))).append(")");
                }
            }
            sb.append(NL).append(indent()).append("    .end()");
        }
    }

    /**
     * Begins a DSL step method call. This is the fallback for EIPs that don't have Jandex-derived primary args
     * generated in the dispatch code.
     */
    protected void beginStep(StringBuilder sb, String name, Object def) {
        handledAttributes.clear();
        optionOwner = def;
        stepStart = sb.length();
        if ("param".equals(name)) {
            inRestParam = true;
        }
        sb.append(NL).append(indent()).append(".").append(name).append("()");
        // enrich().constant("uri"): the expression clause comes first, the options after it
        if ((def instanceof EnrichDefinition || def instanceof PollEnrichDefinition)
                && ((ExpressionNode) def).getExpression() != null) {
            sb.append(NL).append(indent()).append("    .").append(expressionDsl(((ExpressionNode) def).getExpression()));
            handledAttributes.add("expression");
        }
    }

    /**
     * A step the writer writes with its arguments inline ({@code .recipientList(header("to"))}): its options follow.
     */
    protected void beginInlineStep(StringBuilder sb, Object def) {
        optionOwner = def;
        stepStart = sb.length();
    }

    /**
     * Ends a DSL step. For block EIPs that have outputs, appends .end(). For REST sub-builders, appends the
     * corresponding end method (e.g., .endParam(), .endResponseMessage()).
     */
    protected void endStep(StringBuilder sb, String name, Object def) {
        if ("param".equals(name)) {
            inRestParam = false;
        }
        rewriteFromJavaForms(sb, def);
        String restEnd = REST_END_METHODS.get(name);
        if (restEnd != null) {
            sb.append(NL).append(indent()).append(".").append(restEnd).append("()");
        } else if (BLOCK_EIPS.contains(name)) {
            if ("circuitBreaker".equals(name) && pendingFallback != null) {
                Runnable fallback = pendingFallback;
                pendingFallback = null;
                fallback.run();
            }
            sb.append(NL).append(indent()).append(".end()");
        }
    }

    protected void doWriteAttribute(StringBuilder sb, String key, String value, String defaultValue) {
        if (value == null || handledAttributes.contains(key) || SKIP_ATTRIBUTES.contains(key)) {
            if ("customId".equals(key)) {
                currentNodeCustomId = "true".equals(value);
            }
            return;
        }
        if ("id".equals(key) && !generatedIds && !currentNodeCustomId) {
            return;
        }
        if (defaultValue != null && defaultValue.equals(value)) {
            return;
        }
        if (!inSubBuilder && !inDataFormatBuilder && !inRestParam && stepArgumentOption(sb, key)) {
            return;
        }
        String methodName = key;
        if (inDataFormatBuilder) {
            methodName = DATAFORMAT_ATTR_RENAMES.getOrDefault(key, key);
        } else if (!inSubBuilder && !inRestParam) {
            methodName = OPTION_RENAMES.getOrDefault(key, key);
        }
        if (CLASS_LITERAL_ATTRIBUTES.contains(key)) {
            sb.append(NL).append(indent()).append("    .").append(methodName).append("(").append(value).append(".class)");
            return;
        }
        if (inRestParam && "type".equals(key)) {
            sb.append(NL).append(indent()).append("    .type(RestParamType.").append(value).append(")");
            return;
        }
        if (TOGGLE_ATTRIBUTES.contains(key)) {
            if ("true".equals(value)) {
                sb.append(NL).append(indent()).append("    .").append(methodName).append("()");
            }
            return;
        }
        if (BOOLEAN_ATTRIBUTES.contains(key) && ("true".equals(value) || "false".equals(value))) {
            // a placeholder ({{enabled}}) is quoted below, for the String variant of the option
            sb.append(NL).append(indent()).append("    .").append(methodName).append("(").append(value).append(")");
            return;
        }
        if (inSubBuilder && isPrimitiveLiteral(value)) {
            sb.append(NL).append(indent()).append("    .").append(methodName).append("(").append(value).append(")");
            return;
        }
        if (!inSubBuilder && !inDataFormatBuilder && !inRestParam && optionOwner != null) {
            String call = typedOption(optionOwner.getClass(), methodName, value);
            if (call != null) {
                if (!call.isEmpty()) {
                    sb.append(NL).append(indent()).append("    .").append(call);
                }
                return;
            }
        }
        sb.append(NL).append(indent()).append("    .").append(methodName).append("(").append(quote(value)).append(")");
    }

    /**
     * Options the Java DSL takes as arguments of the step itself, not as calls after it: the step's call is written
     * again from its definition, with them. {@code convertBodyTo(byte[].class, "UTF-8")}, {@code removeHeaders("*",
     * "foo")}, {@code toV("direct:x", "send", null)}, {@code log(LoggingLevel.INFO, "my.log", "hi")},
     * {@code sample(10)}, {@code loopDoWhile(...)}.
     *
     * @return whether the option was written as an argument
     */
    private boolean stepArgumentOption(StringBuilder sb, String key) {
        Object d = optionOwner;
        String call = null;
        Set<String> keys = Set.of();
        if (d instanceof ConvertBodyDefinition c && Set.of("charset", "mandatory").contains(key)) {
            String type = classLiteral(c.getType());
            call = c.getCharset() != null
                    ? "convertBodyTo(" + type + ", " + quote(c.getCharset()) + ")"
                    : "convertBodyTo(" + type + ", " + booleanLiteral(c.getMandatory()) + ")";
            keys = Set.of("charset", "mandatory");
        } else if (d instanceof ConvertHeaderDefinition c && Set.of("toName", "charset", "mandatory").contains(key)) {
            call = convertTo("convertHeaderTo", c.getName(), c.getToName(), c.getType(), c.getCharset(), c.getMandatory());
            keys = Set.of("toName", "charset", "mandatory");
        } else if (d instanceof ConvertVariableDefinition c && Set.of("toName", "charset", "mandatory").contains(key)) {
            call = convertTo("convertVariableTo", c.getName(), c.getToName(), c.getType(), c.getCharset(), c.getMandatory());
            keys = Set.of("toName", "charset", "mandatory");
        } else if (d instanceof RemoveHeadersDefinition r && "excludePattern".equals(key)) {
            call = "removeHeaders(" + quote(r.getPattern()) + ", " + quote(r.getExcludePattern()) + ")";
            keys = Set.of("excludePattern");
        } else if (d instanceof ToDefinition t && !(d instanceof ToDynamicDefinition)
                && Set.of("variableSend", "variableReceive").contains(key)) {
            call = "toV(" + quote(t.getUri()) + ", " + quoteOrNull(t.getVariableSend()) + ", "
                   + quoteOrNull(t.getVariableReceive()) + ")";
            keys = Set.of("variableSend", "variableReceive");
        } else if (d != null && d.getClass() == ToDynamicDefinition.class
                && Set.of("variableSend", "variableReceive", "ignoreInvalidEndpoint", "cacheSize").contains(key)) {
            // toD(uri) returns the parent: its options are arguments of toD(uri, ...), one of them at most
            ToDynamicDefinition t = (ToDynamicDefinition) d;
            if (t.getVariableSend() != null || t.getVariableReceive() != null) {
                call = "toD(" + quote(t.getUri()) + ", " + quoteOrNull(t.getVariableSend()) + ", "
                       + quoteOrNull(t.getVariableReceive()) + ")";
            } else if (t.getIgnoreInvalidEndpoint() != null) {
                call = "toD(" + quote(t.getUri()) + ", " + booleanLiteral(t.getIgnoreInvalidEndpoint()) + ")";
            } else if (t.getCacheSize() != null && wholeNumber(t.getCacheSize()) != null) {
                call = "toD(" + quote(t.getUri()) + ", " + t.getCacheSize() + ")";
            }
            keys = Set.of("variableSend", "variableReceive", "ignoreInvalidEndpoint", "cacheSize");
        } else if (d instanceof RemovePropertiesDefinition r && "excludePattern".equals(key)) {
            call = "removeProperties(" + quote(r.getPattern()) + ", " + quote(r.getExcludePattern()) + ")";
            keys = Set.of("excludePattern");
        } else if (d instanceof BeanDefinition b && "beanType".equals(key) && b.getRef() == null) {
            call = "bean(" + classLiteral(b.getBeanType()) + (b.getMethod() != null ? ", " + quote(b.getMethod()) : "") + ")";
            keys = Set.of("beanType", "method");
        } else if (d instanceof LogDefinition l && Set.of("loggingLevel", "logName", "marker").contains(key)) {
            // log(message) returns the parent: its options are arguments of log(level, logName, marker, message)
            String level = "LoggingLevel." + (l.getLoggingLevel() != null ? l.getLoggingLevel() : "INFO");
            if (l.getMarker() != null) {
                call = "log(" + level + ", " + (l.getLogName() != null ? quote(l.getLogName()) : "(String) null") + ", "
                       + quote(l.getMarker()) + ", " + quote(l.getMessage()) + ")";
            } else if (l.getLogName() != null) {
                call = "log(" + level + ", " + quote(l.getLogName()) + ", " + quote(l.getMessage()) + ")";
            } else {
                call = "log(" + level + ", " + quote(l.getMessage()) + ")";
            }
            keys = Set.of("loggingLevel", "logName", "marker");
        } else if (d instanceof SamplingDefinition sd && Set.of("messageFrequency", "samplePeriod").contains(key)) {
            call = sd.getMessageFrequency() != null
                    ? "sample(" + sd.getMessageFrequency() + (isLongLiteral(sd.getMessageFrequency()) ? "L" : "") + ")"
                    : "sample(" + quote(sd.getSamplePeriod()) + ")";
            keys = Set.of("messageFrequency", "samplePeriod");
        } else if (d instanceof RollbackDefinition rb && Set.of("markRollbackOnly", "markRollbackOnlyLast").contains(key)) {
            // rollback() returns the parent: markRollbackOnly() is the step, not an option of it
            if ("true".equals(rb.getMarkRollbackOnly())) {
                call = "markRollbackOnly()";
            } else if ("true".equals(rb.getMarkRollbackOnlyLast())) {
                call = "markRollbackOnlyLast()";
            }
            keys = Set.of("markRollbackOnly", "markRollbackOnlyLast");
        } else if (d instanceof LoopDefinition loop && "doWhile".equals(key)) {
            if ("true".equals(loop.getDoWhile()) && loop.getExpression() != null) {
                call = "loopDoWhile(" + expressionDsl(loop.getExpression()) + ")";
            }
            keys = Set.of("doWhile");
        }
        if (keys.isEmpty()) {
            return false;
        }
        handledAttributes.addAll(keys);
        if (call != null && stepStart >= 0 && stepStart < sb.length()) {
            int end = sb.indexOf(NL, stepStart + 1);
            if (end < 0) {
                end = sb.length();
            }
            String line = sb.substring(stepStart, end);
            int dot = line.indexOf('.');
            if (dot > 0) {
                sb.replace(stepStart, end, line.substring(0, dot + 1) + call);
            }
        }
        return true;
    }

    /**
     * A route built in Java keeps some options in forms XML and YAML do not have (a Class, an array): the step's call
     * is written again with them, without changing the model. {@code throwException(Foo.class, "msg")},
     * {@code removeHeaders("*", "Keep*")}.
     */
    private void rewriteFromJavaForms(StringBuilder sb, Object def) {
        String call = null;
        if (def instanceof ThrowExceptionDefinition t && t.getExceptionType() == null && t.getExceptionClass() != null) {
            call = "throwException(" + typeName(t.getExceptionClass()) + ".class, " + quote(t.getMessage()) + ")";
        } else if (def instanceof RemoveHeadersDefinition r && r.getExcludePattern() == null
                && r.getExcludePatterns() != null && r.getExcludePatterns().length > 0) {
            call = "removeHeaders(" + quote(r.getPattern()) + ", " + quotedList(r.getExcludePatterns()) + ")";
        } else if (def instanceof RemovePropertiesDefinition r && r.getExcludePattern() == null
                && r.getExcludePatterns() != null && r.getExcludePatterns().length > 0) {
            call = "removeProperties(" + quote(r.getPattern()) + ", " + quotedList(r.getExcludePatterns()) + ")";
        }
        if (call == null || stepStart < 0 || stepStart >= sb.length()) {
            return;
        }
        int end = sb.indexOf(NL, stepStart + 1);
        if (end < 0) {
            end = sb.length();
        }
        String line = sb.substring(stepStart, end);
        int dot = line.indexOf('.');
        if (dot > 0) {
            sb.replace(stepStart, end, line.substring(0, dot + 1) + call);
        }
    }

    private String quotedList(String[] values) {
        StringBuilder b = new StringBuilder();
        for (String v : values) {
            b.append(b.isEmpty() ? "" : ", ").append(quote(v));
        }
        return b.toString();
    }

    /** A class as Java names it in source: java.lang.String as String, byte[] as byte[], a nested one with a dot. */
    private String typeName(Class<?> type) {
        if (type.isArray()) {
            return typeName(type.getComponentType()) + "[]";
        }
        String name = type.getName().replace('$', '.');
        return name.startsWith("java.lang.") && name.indexOf('.', 10) < 0 ? name.substring(10) : name;
    }

    /** setHeaders("h1", expr1, "h2", expr2): the Java DSL takes the names and values as varargs of the step. */
    private boolean writeNamesAndValues(StringBuilder sb, String key, List<?> list) {
        StringBuilder args = new StringBuilder();
        for (Object o : list) {
            String name;
            ExpressionDefinition expr;
            if (o instanceof SetHeaderDefinition h) {
                name = h.getName();
                expr = h.getExpression();
            } else if (o instanceof SetVariableDefinition v) {
                name = v.getName();
                expr = v.getExpression();
            } else {
                return false;
            }
            if (name == null || expr == null) {
                return false;
            }
            args.append(args.isEmpty() ? "" : ", ").append(quote(name)).append(", ").append(expressionDsl(expr));
        }
        if (stepStart < 0 || stepStart >= sb.length()) {
            return false;
        }
        int end = sb.indexOf(NL, stepStart + 1);
        if (end < 0) {
            end = sb.length();
        }
        String line = sb.substring(stepStart, end);
        int dot = line.indexOf('.');
        String method = "headers".equals(key) ? "setHeaders" : "setVariables";
        sb.replace(stepStart, end, line.substring(0, dot + 1) + method + "(" + args + ")");
        handledAttributes.add(key);
        return true;
    }

    private String convertTo(String method, String name, String toName, String type, String charset, String mandatory) {
        String cls = classLiteral(type);
        if (toName != null) {
            return method + "(" + quote(name) + ", " + quote(toName) + ", " + cls + ")";
        }
        if (charset != null) {
            return method + "(" + quote(name) + ", " + cls + ", " + quote(charset) + ")";
        }
        return method + "(" + quote(name) + ", " + cls + ", " + booleanLiteral(mandatory) + ")";
    }

    private static String booleanLiteralOrFalse(String value) {
        return "true".equalsIgnoreCase(value) ? "true" : "false";
    }

    private static String booleanLiteral(String value) {
        return "false".equalsIgnoreCase(value) ? "false" : "true";
    }

    private String quoteOrNull(String value) {
        return value != null ? quote(value) : "null";
    }

    /** Options whose fluent method in the Java DSL has another name than the option. */
    private static final Map<String, String> OPTION_RENAMES = Map.of("errorHandlerRef", "errorHandler");

    /**
     * How an option is written when its fluent method does not take a String: {@code name()} for a toggle (nothing when
     * false), {@code name(10)} for a number (a duration such as {@code 2s} in millis), {@code name(true)},
     * {@code name(LoggingLevel.WARN)}. Null when a {@code name(String)} method exists (the value is quoted, as a
     * placeholder must be) or no method fits; the empty string when nothing is to be written.
     */
    static String typedOption(Class<?> type, String name, String value) {
        Method noArg = null;
        List<Class<?>> params = new ArrayList<>();
        for (Method m : type.getMethods()) {
            if (!m.getName().equals(name) || m.isBridge() || Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            if (m.getParameterCount() == 0) {
                noArg = m;
            } else if (m.getParameterCount() == 1) {
                params.add(m.getParameterTypes()[0]);
            }
        }
        if (params.contains(String.class) || params.contains(Object.class)) {
            return null;
        }
        String v = value.strip();
        // a property placeholder can only go where a String does
        if (v.startsWith("{{")) {
            return null;
        }
        for (Class<?> p : params) {
            if ((p == boolean.class || p == Boolean.class) && ("true".equals(v) || "false".equals(v))) {
                return name + "(" + v + ")";
            }
            if (p == int.class || p == Integer.class || p == long.class || p == Long.class) {
                String number = wholeNumber(v);
                if (number != null) {
                    return name + "(" + number + ((p == long.class || p == Long.class) && isLongLiteral(number) ? "L" : "")
                           + ")";
                }
            }
            if (p == double.class || p == Double.class || p == float.class || p == Float.class) {
                try {
                    Double.parseDouble(v);
                    return name + "(" + v + (p == float.class || p == Float.class ? "f" : "") + ")";
                } catch (NumberFormatException e) {
                    // not a number
                }
            }
            if (p.isEnum()) {
                for (Object c : p.getEnumConstants()) {
                    if (((Enum<?>) c).name().equalsIgnoreCase(v)) {
                        return name + "(" + p.getSimpleName() + "." + ((Enum<?>) c).name() + ")";
                    }
                }
            }
        }
        if (noArg != null && params.isEmpty()) {
            return "true".equals(v) ? name + "()" : "";
        }
        // mode="BeforeConsumer" is modeBeforeConsumer()
        if (params.isEmpty() && !v.isEmpty()) {
            String combined = name + Character.toUpperCase(v.charAt(0)) + v.substring(1);
            try {
                if (type.getMethod(combined).getParameterCount() == 0) {
                    return combined + "()";
                }
            } catch (NoSuchMethodException e) {
                // not that either
            }
        }
        return null;
    }

    /** A whole number, or a duration such as 2s or 1m30s in millis; null for anything else. */
    private static String wholeNumber(String value) {
        try {
            return String.valueOf(Long.parseLong(value));
        } catch (NumberFormatException e) {
            try {
                return String.valueOf(TimeUtils.toMilliSeconds(value));
            } catch (RuntimeException ex) {
                return null;
            }
        }
    }

    private static boolean isLongLiteral(String number) {
        long l = Long.parseLong(number);
        return l > Integer.MAX_VALUE || l < Integer.MIN_VALUE;
    }

    private static boolean isPrimitiveLiteral(String value) {
        if ("true".equals(value) || "false".equals(value)) {
            return true;
        }
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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
            if ("onFallback".equals(key)) {
                // onFallback() takes every step after it: it is written last, before the circuit breaker's end()
                pendingFallback = () -> {
                    indentLevel++;
                    sb.append(NL).append(indent()).append(".onFallback()");
                    writer.accept(sb, value);
                    indentLevel--;
                };
            } else if (BLOCK_CHILDREN.contains(key)) {
                indentLevel++;
                sb.append(NL).append(indent()).append(".").append(key).append("()");
                writer.accept(sb, value);
                indentLevel--;
            } else if (value instanceof StreamResequencerConfig src) {
                writeStreamResequencerConfig(sb, src);
            } else if (value instanceof ExpressionSubElementDefinition esd) {
                if (esd.getExpressionType() != null) {
                    boolean isPredicate = PREDICATE_CHILDREN.contains(key);
                    sb.append(NL).append(indent()).append("    .").append(CHILD_RENAMES.getOrDefault(key, key)).append("(");
                    if (isPredicate) {
                        sb.append("(Predicate) ");
                    }
                    sb.append(expressionDsl(esd.getExpressionType())).append(")");
                }
            } else if (value instanceof OnWhenDefinition owd) {
                if (owd.getExpression() != null) {
                    sb.append(NL).append(indent()).append("    .onWhen(")
                            .append(expressionDsl(owd.getExpression())).append(")");
                }
            } else if (SUB_BUILDER_CHILDREN.contains(key)) {
                sb.append(NL).append(indent()).append("    .").append(key).append("()");
                inSubBuilder = true;
                writer.accept(sb, value);
                inSubBuilder = false;
                sb.append(NL).append(indent()).append("    .end()");
            } else if (value instanceof BatchResequencerConfig brc) {
                writeBatchResequencerConfig(sb, brc);
            } else if (value instanceof LangChain4jTokenizerDefinition ltd) {
                writeTokenizerBuilder(sb, key, ltd);
            } else if (value instanceof DataFormatDefinition df) {
                writeDataFormatBuilder(sb, key, df, writer);
            } else if (value instanceof LoadBalancerDefinition lb) {
                writeLoadBalancerType(sb, lb);
            } else if (value instanceof ToDefinition td) {
                sb.append(NL).append(indent()).append("    .to(");
                if (td.getPattern() != null) {
                    sb.append("ExchangePattern.").append(td.getPattern()).append(", ");
                }
                if (td.getUri() != null) {
                    sb.append(quote(td.getUri()));
                }
                sb.append(")");
            } else {
                writer.accept(sb, value);
            }
        }
    }

    protected <T> void doWriteElementRef(StringBuilder sb, String key, T value, BiConsumer<StringBuilder, T> writer) {
        if (value != null && !handledAttributes.contains(key)) {
            // a route's input and output types are options of the route: .inputType("urn")
            if (value instanceof InputTypeDefinition in && in.getUrn() != null) {
                sb.append(NL).append(indent()).append(".inputType")
                        .append("true".equals(in.getValidate()) ? "WithValidate" : "")
                        .append("(").append(quote(in.getUrn())).append(")");
                return;
            }
            if (value instanceof OutputTypeDefinition out && out.getUrn() != null) {
                sb.append(NL).append(indent()).append(".outputType")
                        .append("true".equals(out.getValidate()) ? "WithValidate" : "")
                        .append("(").append(quote(out.getUrn())).append(")");
                return;
            }
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
            if (("headers".equals(key) && optionOwner instanceof SetHeadersDefinition
                    || "variables".equals(key) && optionOwner instanceof SetVariablesDefinition)
                    && writeNamesAndValues(sb, key, list)) {
                return;
            }
            if ("value".equals(key) && list.get(0) instanceof ValueDefinition) {
                writeAllowableValues(sb, list);
                return;
            }
            if ("header".equals(key) && list.get(0) instanceof ResponseHeaderDefinition) {
                writeResponseHeaders(sb, list);
                return;
            }
            if ("parameter".equals(key) && list.get(0) instanceof TemplatedRouteParameterDefinition) {
                writeTemplatedRouteParameters(sb, list);
                return;
            }
            if (list.get(0) instanceof RestPropertyDefinition) {
                writeRestProperties(sb, key, list);
                return;
            }
            if (("bean".equals(key) || "templateBean".equals(key)) && list.get(0) instanceof BeanFactoryDefinition) {
                writeBeanDefinitions(sb, list);
                return;
            }
            for (T item : list) {
                if (item instanceof PropertyExpressionDefinition ped) {
                    sb.append(NL).append(indent()).append("    .option(").append(quote(ped.getKey()))
                            .append(", ").append(expressionDsl(ped.getExpression())).append(")");
                } else {
                    indentLevel++;
                    writer.accept(sb, item);
                    indentLevel--;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void writeAllowableValues(StringBuilder sb, List<T> list) {
        sb.append(NL).append(indent()).append("    .allowableValues(");
        boolean first = true;
        for (T item : list) {
            ValueDefinition vd = (ValueDefinition) item;
            if (vd.getValue() != null) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(quote(vd.getValue()));
            }
        }
        sb.append(")");
    }

    @SuppressWarnings("unchecked")
    private <T> void writeResponseHeaders(StringBuilder sb, List<T> list) {
        for (T item : list) {
            ResponseHeaderDefinition h = (ResponseHeaderDefinition) item;
            sb.append(NL).append(indent()).append("    .header(").append(quote(h.getName())).append(")");
            if (h.getDescription() != null) {
                sb.append(NL).append(indent()).append("        .description(").append(quote(h.getDescription())).append(")");
            }
            if (h.getDataType() != null && !"string".equals(h.getDataType())) {
                sb.append(NL).append(indent()).append("        .dataType(").append(quote(h.getDataType())).append(")");
            }
            if (h.getDataFormat() != null) {
                sb.append(NL).append(indent()).append("        .dataFormat(").append(quote(h.getDataFormat())).append(")");
            }
            if (h.getAllowableValues() != null && !h.getAllowableValues().isEmpty()) {
                writeAllowableValues(sb, h.getAllowableValues());
            }
            if (h.getExample() != null) {
                sb.append(NL).append(indent()).append("        .example(").append(quote(h.getExample())).append(")");
            }
            sb.append(NL).append(indent()).append("    .endHeader()");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void writeTemplatedRouteParameters(StringBuilder sb, List<T> list) {
        for (T item : list) {
            TemplatedRouteParameterDefinition param = (TemplatedRouteParameterDefinition) item;
            sb.append(NL).append(indent()).append("    .parameter(")
                    .append(quote(param.getName())).append(", ")
                    .append(quote(param.getValue())).append(")");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void writeRestProperties(StringBuilder sb, String key, List<T> list) {
        for (T item : list) {
            RestPropertyDefinition prop = (RestPropertyDefinition) item;
            sb.append(NL).append(indent()).append("    .").append(key).append("(")
                    .append(quote(prop.getKey())).append(", ")
                    .append(quote(prop.getValue())).append(")");
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
            inDataFormatBuilder = true;
            writer.accept(sb, (T) df);
            inDataFormatBuilder = false;
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

    /** deadLetterChannel("uri").maximumRedeliveries(3), defaultErrorHandler() or noErrorHandler(). */
    private String errorHandlerDsl(ErrorHandlerDefinition errorHandler) {
        StringBuilder sb = new StringBuilder();
        if (errorHandler.getErrorHandlerType() instanceof DeadLetterChannelDefinition dlc) {
            sb.append("deadLetterChannel(").append(quote(dlc.getDeadLetterUri())).append(")");
            appendErrorHandlerOptions(sb, dlc);
        } else if (errorHandler.getErrorHandlerType() instanceof DefaultErrorHandlerDefinition deh) {
            sb.append("defaultErrorHandler()");
            appendErrorHandlerOptions(sb, deh);
        } else {
            sb.append("noErrorHandler()");
        }
        return sb.toString();
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

    /**
     * resequence().stream() with its options: capacity(int), timeout(long) and deliveryAttemptInterval(long) in millis.
     */
    protected void writeStreamResequencerConfig(StringBuilder sb, StreamResequencerConfig src) {
        sb.append(NL).append(indent()).append("    .stream()");
        if (src.getCapacity() != null) {
            sb.append(NL).append(indent()).append("    .capacity(").append(src.getCapacity()).append(")");
        }
        if (src.getTimeout() != null) {
            String millis = wholeNumber(src.getTimeout());
            sb.append(NL).append(indent()).append("    .timeout(").append(millis != null ? millis : src.getTimeout())
                    .append(")");
        }
        if (src.getDeliveryAttemptInterval() != null) {
            String millis = wholeNumber(src.getDeliveryAttemptInterval());
            sb.append(NL).append(indent()).append("    .deliveryAttemptInterval(")
                    .append(millis != null ? millis : src.getDeliveryAttemptInterval()).append(")");
        }
        if ("true".equals(src.getIgnoreInvalidExchanges())) {
            sb.append(NL).append(indent()).append("    .ignoreInvalidExchanges()");
        }
        if ("true".equals(src.getRejectOld())) {
            sb.append(NL).append(indent()).append("    .rejectOld()");
        }
    }

    protected void writeBatchResequencerConfig(StringBuilder sb, BatchResequencerConfig brc) {
        sb.append(NL).append(indent()).append("    .batch()");
        if (brc.getBatchSize() != null) {
            sb.append(NL).append(indent()).append("    .size(").append(brc.getBatchSize()).append(")");
        }
        if (brc.getBatchTimeout() != null) {
            // timeout(long): a duration (1s) as millis
            String millis = wholeNumber(brc.getBatchTimeout());
            sb.append(NL).append(indent()).append("    .timeout(")
                    .append(millis != null ? millis : brc.getBatchTimeout()).append(")");
        }
    }

    private void writeTokenizerBuilder(StringBuilder sb, String key, LangChain4jTokenizerDefinition def) {
        // backtrack .tokenizer() → .tokenize(tokenizer().byXxx()...end())
        String str = sb.toString();
        if (str.endsWith(".tokenizer()")) {
            sb.setLength(sb.length() - ".tokenizer()".length());
        }
        String builderMethod = switch (key) {
            case "langChain4jWordTokenizer" -> "byWord";
            case "langChain4jLineTokenizer" -> "byLine";
            case "langChain4jSentenceTokenizer" -> "bySentence";
            case "langChain4jParagraphTokenizer" -> "byParagraph";
            case "langChain4jCharacterTokenizer" -> "byCharacter";
            default -> throw new IllegalArgumentException("Unknown tokenizer type: " + key);
        };
        sb.append(".tokenize(tokenizer()").append(NL);
        sb.append(indent()).append("        .").append(builderMethod).append("()");
        if (def.getMaxTokens() != null) {
            sb.append(NL).append(indent()).append("            .maxSegmentSize(").append(def.getMaxTokens()).append(")");
        }
        if (def.getMaxOverlap() != null) {
            sb.append(NL).append(indent()).append("            .maxOverlap(").append(def.getMaxOverlap()).append(")");
        }
        if (def.getTokenizerType() != null) {
            sb.append(NL).append(indent()).append("            .using(LangChain4jTokenizerDefinition.TokenizerType.")
                    .append(def.getTokenizerType()).append(")");
        }
        sb.append(NL).append(indent()).append("            .end())");
        // mark all attributes as handled so generated writer doesn't re-emit them
        handledAttributes.add("maxTokens");
        handledAttributes.add("maxOverlap");
        handledAttributes.add("tokenizerType");
        handledAttributes.add("modelName");
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
            } else if (failover.getExceptionTypes() != null && !failover.getExceptionTypes().isEmpty()) {
                // built in Java: failover(IOException.class) keeps the classes
                boolean first = true;
                for (Class<?> ex : failover.getExceptionTypes()) {
                    if (!first) {
                        sb.append(", ");
                    }
                    first = false;
                    sb.append(typeName(ex)).append(".class");
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
        } else if (lb instanceof CustomLoadBalancerDefinition custom && custom.getRef() != null) {
            sb.append(".custom(").append(quote(custom.getRef())).append(")");
            handledAttributes.add("ref");
        } else if (lb instanceof WeightedLoadBalancerDefinition w && w.getDistributionRatio() != null) {
            sb.append(".weighted(").append(booleanLiteralOrFalse(w.getRoundRobin())).append(", ")
                    .append(quote(w.getDistributionRatio()));
            if (w.getDistributionRatioDelimiter() != null && !",".equals(w.getDistributionRatioDelimiter())) {
                sb.append(", ").append(quote(w.getDistributionRatioDelimiter()));
            }
            sb.append(")");
            handledAttributes.add("roundRobin");
            handledAttributes.add("distributionRatio");
            handledAttributes.add("distributionRatioDelimiter");
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
        // a route built in Java keeps setProperty("p").constant("v") or header("x") as a Java object wrapped in an
        // ExpressionDefinition: the language inside it is written (the model is not changed)
        ExpressionDefinition language = languageOf(expr);
        if (language != expr) {
            return expressionDsl(language);
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

    /** The language inside a wrapped expression clause or value builder; the expression itself when there is none. */
    private static ExpressionDefinition languageOf(ExpressionDefinition expr) {
        if (expr.getClass() != ExpressionDefinition.class) {
            return expr;
        }
        Object v = expr.getExpressionValue() != null ? expr.getExpressionValue() : expr.getPredicate();
        for (int i = 0; i < 5 && v != null; i++) {
            if (v instanceof ExpressionDefinition d) {
                return d;
            } else if (v instanceof ExpressionClause<?> clause) {
                v = clause.getExpressionType() instanceof ExpressionDefinition d ? d : clause.getExpressionValue();
            } else if (v instanceof ValueBuilder vb) {
                v = vb.getExpression();
            } else {
                break;
            }
        }
        return expr;
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
        if (expr instanceof DatasonnetExpression) {
            return "datasonnet(" + quotedValue + ")";
        }
        if (expr instanceof TokenizerExpression t) {
            // the token is an option, not the expression text: the language builder keeps them all
            StringBuilder b = new StringBuilder("expression().tokenize()");
            appendOption(b, "token", t.getToken());
            appendOption(b, "endToken", t.getEndToken());
            appendOption(b, "inheritNamespaceTagName", t.getInheritNamespaceTagName());
            appendOption(b, "regex", t.getRegex());
            appendOption(b, "xml", t.getXml());
            appendOption(b, "includeTokens", t.getIncludeTokens());
            appendOption(b, "group", t.getGroup());
            appendOption(b, "groupDelimiter", t.getGroupDelimiter());
            appendOption(b, "skipFirst", t.getSkipFirst());
            if (t.getSource() != null) {
                appendOption(b, "source", t.getSource());
            }
            return b.append(".end()").toString();
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
        String factoryMethod = languageFactoryMethod(expr);
        if (factoryMethod != null) {
            // groovy, ognl, ...: as their own expression, not language("groovy", ...)
            return "expression()." + factoryMethod + "(" + quotedValue + ").end()";
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
        String factoryMethod = languageFactoryMethod(expr);
        if (factoryMethod != null) {
            return factoryMethod;
        }
        String lang = expr.getLanguage();
        if (lang != null && !lang.isEmpty()) {
            return "language";
        }
        return null;
    }

    /**
     * The method of expression() that builds this kind of expression from its text (groovy("..."), ognl("...")), or
     * null when there is none.
     */
    private static String languageFactoryMethod(ExpressionDefinition expr) {
        String lang = expr.getLanguage();
        if (lang == null || lang.isEmpty() || expr instanceof LanguageExpression) {
            return null;
        }
        try {
            Method m = LanguageBuilderFactory.class.getMethod(lang, String.class);
            return m.getReturnType().getEnclosingClass() == expr.getClass() ? lang : null;
        } catch (NoSuchMethodException e) {
            return null;
        }
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

        // the xml namespaces of xpath, xquery and xtokenize (those of the xml document the route was read from)
        if (expr instanceof NamespaceAwareExpression nae && nae.getNamespaces() != null
                && !nae.getNamespaces().isEmpty()) {
            opts.append(".namespaces(Map.of(");
            StringJoiner pairs = new StringJoiner(", ");
            nae.getNamespaces().forEach((prefix, uri) -> pairs.add(quote(prefix) + ", " + quote(uri)));
            opts.append(pairs).append("))");
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
        // escape as a java string literal (a line break is not allowed in a string literal)
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t")
               + "\"";
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
