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
package org.apache.camel.updates.camel40.java;

import java.beans.SimpleBeanInfo;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.camel.updates.AbstractCamelJavaVisitor;
import org.apache.camel.updates.RecipesUtil;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.ImplementInterface;
import org.openrewrite.java.RemoveImplements;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

/**
 * Recipe migrating changes between Camel 3.x to 4.x, for more details see the
 * <a href="https://camel.apache.org/manual/camel-4-migration-guide.html#_api_changes">documentation</a>.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class CamelAPIsRecipe extends Recipe {

    private static final String MATCHER_CONTEXT_GET_ENDPOINT_MAP = "org.apache.camel.CamelContext getEndpointMap()";
    private static final String MATCHER_CONTEXT_GET_EXT = "org.apache.camel.CamelContext getExtension(java.lang.Class)";
    private static final String M_PRODUCER_TEMPLATE_ASYNC_CALLBACK = "org.apache.camel.ProducerTemplate asyncCallback(..)";
    private static final String M_CONTEXT_ADAPT = "org.apache.camel.CamelContext adapt(java.lang.Class)";
    private static final String M_CONTEXT_SET_DUMP_ROUTES = "org.apache.camel.CamelContext setDumpRoutes(java.lang.Boolean)";
    private static final String M_CONTEXT_IS_DUMP_ROUTES = "org.apache.camel.CamelContext isDumpRoutes()";
    private static final String M_EXCHANGE_ADAPT = "org.apache.camel.Exchange adapt(java.lang.Class)";
    private static final String M_EXCHANGE_GET_PROPERTY
            = "org.apache.camel.Exchange getProperty(org.apache.camel.ExchangePropertyKey)";
    private static final String M_EXCHANGE_REMOVE_PROPERTY
            = "org.apache.camel.Exchange removeProperty(org.apache.camel.ExchangePropertyKey)";
    private static final String M_EXCHANGE_SET_PROPERTY = "org.apache.camel.Exchange setProperty(..)";
    private static final String M_CATALOG_ARCHETYPE_AS_XML = "org.apache.camel.catalog.CamelCatalog archetypeCatalogAsXml()";

    @Override
    public String getDisplayName() {
        return "Camel API changes";
    }

    @Override
    public String getDescription() {
        return "Apache Camel API migration from version 3.20 or higher to 4.0. Removal of deprecated APIs.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return RecipesUtil.newVisitor(new AbstractCamelJavaVisitor() {

            //Cache for all methodInvocations CamelContext adapt(java.lang.Class).
            private Map<UUID, Tree> adaptCache = new HashMap<>();

            @Override
            protected J.Import doVisitImport(J.Import _import, ExecutionContext context) {
                J.Import im = super.doVisitImport(_import, context);

                //Removed Discard and DiscardOldest from org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy.
                if (im.isStatic() && im.getTypeName().equals("org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy")
                        && im.getQualid() != null
                        && ("Discard".equals(im.getQualid().getSimpleName())
                                || "DiscardOldest".equals(im.getQualid().getSimpleName()))) {
                    Comment comment = RecipesUtil.createMultinlineComment(String.format(
                            "'ThreadPoolRejectedPolicy.%s' has been removed, consider using 'ThreadPoolRejectedPolicy.Abort'.",
                            im.getQualid().getSimpleName()));
                    im = im.withComments(Collections.singletonList(comment));

                }
                //Removed org.apache.camel.builder.SimpleBuilder.
                // Was mostly used internally in Camel with the Java DSL in some situations.
                else if ("org.apache.camel.builder.SimpleBuilder".equals(im.getTypeName())) {
                    Comment comment = RecipesUtil.createMultinlineComment(String.format(
                            "'%s' has been removed, (class was used internally).", SimpleBeanInfo.class.getCanonicalName()));
                    im = im.withComments(Collections.singletonList(comment));

                }

                //Move the following class from org.apache.camel.api.management.mbean.BacklogTracerEventMessage in camel-management-api JAR to org.apache.camel.spi.BacklogTracerEventMessage in camel-api JAR.
                //
                // BacklogTracerEventMessage moved from `org.apache.camel.api.management.mbean.BacklogTracerEventMessage`
                // to  `org.apache.camel.spi.BacklogTracerEventMessage`
                doAfterVisit(
                        new ChangeType(
                                "org.apache.camel.api.management.mbean.BacklogTracerEventMessage",
                                "org.apache.camel.spi.BacklogTracerEventMessage", true).getVisitor());

                return im;
            }

            @Override
            protected J.ClassDeclaration doVisitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext context) {
                J.ClassDeclaration cd = super.doVisitClassDeclaration(classDecl, context);

                //Removed org.apache.camel.spi.OnCamelContextStart. Use org.apache.camel.spi.OnCamelContextStarting instead.
                if (cd.getImplements() != null && cd.getImplements().stream()
                        .anyMatch(f -> TypeUtils.isOfClassType(f.getType(), "org.apache.camel.spi.OnCamelContextStart"))) {

                    doAfterVisit(new ImplementInterface<ExecutionContext>(cd, "org.apache.camel.spi.OnCamelContextStarting"));
                    doAfterVisit(new RemoveImplements("org.apache.camel.spi.OnCamelContextStart", null).getVisitor());

                } //Removed org.apache.camel.spi.OnCamelContextStop. Use org.apache.camel.spi.OnCamelContextStopping instead.
                else if (cd.getImplements() != null && cd.getImplements().stream()
                        .anyMatch(f -> TypeUtils.isOfClassType(f.getType(), "org.apache.camel.spi.OnCamelContextStop"))) {

                    doAfterVisit(new ImplementInterface<ExecutionContext>(cd, "org.apache.camel.spi.OnCamelContextStopping"));
                    doAfterVisit(new RemoveImplements("org.apache.camel.spi.OnCamelContextStop", null).getVisitor());

                }
                return cd;
            }

            @Override
            protected J.FieldAccess doVisitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext context) {
                J.FieldAccess fa = super.doVisitFieldAccess(fieldAccess, context);
                //The org.apache.camel.ExchangePattern has removed InOptionalOut.
                if ("InOptionalOut".equals(fieldAccess.getSimpleName()) && fa.getType() != null
                        && fa.getType().isAssignableFrom(Pattern.compile("org.apache.camel.ExchangePattern"))) {
                    return fa.withName(new J.Identifier(
                            UUID.randomUUID(), fa.getPrefix(), Markers.EMPTY, Collections.emptyList(),
                            "/* " + fa.getSimpleName() + " has been removed */", fa.getType(), null));
                }

                else if (("Discard".equals(fa.getSimpleName()) || "DiscardOldest".equals(fa.getSimpleName()))
                        && fa.getType() != null && fa.getType().isAssignableFrom(
                                Pattern.compile("org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy"))) {
                    Comment comment = RecipesUtil.createMultinlineComment(String.format(
                            "'ThreadPoolRejectedPolicy.%s' has been removed, consider using 'ThreadPoolRejectedPolicy.Abort'.",
                            fa.getSimpleName()));
                    fa = fa.withComments(Collections.singletonList(comment));

                }

                return fa;
            }

            @Override
            protected J.MethodDeclaration doVisitMethodDeclaration(J.MethodDeclaration method, ExecutionContext context) {
                J.MethodDeclaration md = super.doVisitMethodDeclaration(method, context);

                //Method 'configure' was removed from `org.apache.camel.main.MainListener`, consider using 'beforeConfigure' or 'afterConfigure'.
                if ("configure".equals(md.getSimpleName())
                        && JavaType.Primitive.Void.equals(md.getReturnTypeExpression().getType())
                        && md.getMethodType().getDeclaringType()
                                .isAssignableFrom(Pattern.compile("org.apache.camel.main.MainListener"))
                        && !md.getParameters().isEmpty()
                        && md.getParameters().size() == 1
                        && md.getParameters().get(0) instanceof J.VariableDeclarations
                        && ((J.VariableDeclarations) md.getParameters().get(0)).getType()
                                .isAssignableFrom(Pattern.compile("org.apache.camel.CamelContext"))) {
                    Comment comment = RecipesUtil.createMultinlineComment(String.format(
                            " Method '%s' was removed from `%s`, consider using 'beforeConfigure' or 'afterConfigure'. ",
                            md.getSimpleName(), "org.apache.camel.main.MainListener"));
                    md = md.withComments(Collections.singletonList(comment));
                }

                return md;
            }

            @Override
            protected J.Annotation doVisitAnnotation(J.Annotation annotation, ExecutionContext context) {
                J.Annotation a = super.doVisitAnnotation(annotation, context);

                //Removed @FallbackConverter as you should use @Converter(fallback = true) instead.
                if (a.getType().toString().equals("org.apache.camel.FallbackConverter")) {
                    maybeAddImport("org.apache.camel.Converter", null, false);
                    maybeRemoveImport("org.apache.camel.FallbackConverter");

                    return RecipesUtil.createAnnotation(annotation, "Converter", null, "fallback = true");
                }
                //Removed uri attribute on @EndpointInject, @Produce, and @Consume as you should use value (default) instead.
                //For example @Produce(uri = "kafka:cheese") should be changed to @Produce("kafka:cheese")
                else if (a.getType().toString().equals("org.apache.camel.EndpointInject")) {
                    Optional<String> originalValue = RecipesUtil.getValueOfArgs(a.getArguments(), "uri");
                    if (originalValue.isPresent()) {
                        return RecipesUtil.createAnnotation(annotation, "EndpointInject", s -> s.startsWith("uri="),
                                originalValue.get());
                    }
                }
                //Removed uri attribute on @EndpointInject, @Produce, and @Consume as you should use value (default) instead.
                //For example @Produce(uri = "kafka:cheese") should be changed to @Produce("kafka:cheese")
                else if (a.getType().toString().equals("org.apache.camel.Produce")) {
                    Optional<String> originalValue = RecipesUtil.getValueOfArgs(a.getArguments(), "uri");
                    if (originalValue.isPresent()) {
                        return RecipesUtil.createAnnotation(annotation, "Produce", s -> s.startsWith("uri="),
                                originalValue.get());
                    }
                }
                //Removed uri attribute on @EndpointInject, @Produce, and @Consume as you should use value (default) instead.
                //For example @Produce(uri = "kafka:cheese") should be changed to @Produce("kafka:cheese")
                else if (a.getType().toString().equals("org.apache.camel.Consume")) {
                    Optional<String> originalValue = RecipesUtil.getValueOfArgs(a.getArguments(), "uri");
                    if (originalValue.isPresent()) {
                        return RecipesUtil.createAnnotation(annotation, "Consume", s -> s.startsWith("uri="),
                                originalValue.get());
                    }
                }
                // Removed label on @UriEndpoint as you should use category instead.
                else if (a.getType().toString().equals("org.apache.camel.spi.UriEndpoint")) {

                    Optional<String> originalValue = RecipesUtil.getValueOfArgs(a.getArguments(), "label");
                    if (originalValue.isPresent()) {
                        maybeAddImport("org.apache.camel.Category", null, false);

                        String newValue;
                        try {
                            newValue = RecipesUtil.Category.valueOf(originalValue.get().toUpperCase().replaceAll("\"", ""))
                                    .getValue();
                        } catch (IllegalArgumentException e) {
                            newValue = originalValue.get() + "/*unknown_value*/";
                        }

                        return RecipesUtil.createAnnotation(annotation, "UriEndpoint", s -> s.startsWith("label="),
                                "category = {Category." + newValue + "}");
                    }
                }

                return a;
            }

            @Override
            protected J.MethodInvocation doVisitMethodInvocation(J.MethodInvocation method, ExecutionContext context) {
                J.MethodInvocation mi = super.doVisitMethodInvocation(method, context);

                //if adapt method invocation is used as a select for another method invocation, it is replaced
                if (mi.getSelect() != null && adaptCache.containsKey(mi.getSelect().getId())) {
                    getCursor().putMessage("adapt_cast", mi.getSelect().getId());
                } else
                // context.getExtension(ExtendedCamelContext.class).getComponentNameResolver() -> PluginHelper.getComponentNameResolver(context)
                if (getMethodMatcher(MATCHER_CONTEXT_GET_ENDPOINT_MAP).matches(mi, false)) {
                    mi = mi.withName(new J.Identifier(
                            UUID.randomUUID(), mi.getPrefix(), Markers.EMPTY, Collections.emptyList(),
                            "/* " + mi.getSimpleName() + " has been removed, consider getEndpointRegistry() instead */",
                            mi.getType(), null));
                }
                // ProducerTemplate.asyncCallback() has been replaced by 'asyncSend(') or 'asyncRequest()'
                else if (getMethodMatcher(M_PRODUCER_TEMPLATE_ASYNC_CALLBACK).matches(mi, false)) {
                    Comment comment = RecipesUtil.createMultinlineComment(String.format(
                            " Method '%s()' has been replaced by 'asyncSend()' or 'asyncRequest()'.", mi.getSimpleName()));
                    mi = mi.withComments(Collections.singletonList(comment));
                }
                //context.adapt(ModelCamelContext.class) -> ((ModelCamelContext) context)
                else if (getMethodMatcher(M_CONTEXT_ADAPT).matches(mi, false)) {
                    if (mi.getType().isAssignableFrom(Pattern.compile("org.apache.camel.model.ModelCamelContext"))) {
                        J.Identifier type
                                = RecipesUtil.createIdentifier(mi.getPrefix(), "ModelCamelContext", "java.lang.Object");
                        J.ControlParentheses<?> cp
                                = RecipesUtil.createParentheses(RecipesUtil.createTypeCast(type, mi.getSelect()));
                        //put the type cast into cache in case it is replaced lately
                        mi = mi.withComments(
                                Collections.singletonList(RecipesUtil.createMultinlineComment("Method 'adapt' was removed.")));
                        adaptCache.put(method.getId(), cp);
                    } else if (mi.getType().isAssignableFrom(Pattern.compile("org.apache.camel.ExtendedCamelContext"))) {
                        mi = mi.withName(mi.getName().withSimpleName("getCamelContextExtension"))
                                .withArguments(Collections.emptyList());
                        maybeRemoveImport("org.apache.camel.ExtendedCamelContext");
                    }
                }
                //exchange.adapt(ExtendedExchange.class) -> exchange.getExchangeExtension()
                else if (getMethodMatcher(M_EXCHANGE_ADAPT).matches(mi, false)
                        && mi.getType().isAssignableFrom(Pattern.compile("org.apache.camel.ExtendedExchange"))) {
                    mi = mi.withName(mi.getName().withSimpleName("getExchangeExtension"))
                            .withArguments(Collections.emptyList());
                    maybeRemoveImport("org.apache.camel.ExtendedExchange");
                }
                //newExchange.getProperty(ExchangePropertyKey.FAILURE_HANDLED) -> newExchange.getExchangeExtension().isFailureHandled()
                else if (getMethodMatcher(M_EXCHANGE_GET_PROPERTY).matches(mi, false)
                        && mi.getArguments().get(0).toString().endsWith("FAILURE_HANDLED")) {
                    mi = mi.withName(mi.getName().withSimpleName("getExchangeExtension().isFailureHandled"))
                            .withArguments(Collections.emptyList());
                    maybeRemoveImport("org.apache.camel.ExchangePropertyKey");
                }
                //exchange.removeProperty(ExchangePropertyKey.FAILURE_HANDLED); -> exchange.getExchangeExtension().setFailureHandled(false);
                else if (getMethodMatcher(M_EXCHANGE_REMOVE_PROPERTY).matches(mi, false)
                        && mi.getArguments().get(0).toString().endsWith("FAILURE_HANDLED")) {
                    mi = mi.withName(mi.getName().withSimpleName("getExchangeExtension().setFailureHandled")).withArguments(
                            Collections.singletonList(RecipesUtil.createIdentifier(Space.EMPTY, "false", "java.lang.Boolean")));
                    maybeRemoveImport("org.apache.camel.ExchangePropertyKey");
                }
                //exchange.setProperty(ExchangePropertyKey.FAILURE_HANDLED, failureHandled); -> exchange.getExchangeExtension().setFailureHandled(failureHandled);
                else if (getMethodMatcher(M_EXCHANGE_SET_PROPERTY).matches(mi, false)
                        && mi.getArguments().get(0).toString().endsWith("FAILURE_HANDLED")) {
                    mi = mi.withName(mi.getName()
                            .withSimpleName("getExchangeExtension().setFailureHandled"))
                            .withArguments(Collections.singletonList(mi.getArguments().get(1).withPrefix(Space.EMPTY)));
                    maybeRemoveImport("org.apache.camel.ExchangePropertyKey");
                }
                //'org.apache.camel.catalogCamelCatalog.archetypeCatalogAsXml()` has been removed
                else if (getMethodMatcher(M_CATALOG_ARCHETYPE_AS_XML).matches(mi, false)) {
                    mi = mi.withComments(Collections.singletonList(
                            RecipesUtil.createMultinlineComment(" Method '" + mi.getSimpleName() + "' has been removed. ")));
                }
                //context().setDumpRoutes(true); -> context().setDumpRoutes("xml");(or "yaml")
                else if (getMethodMatcher(M_CONTEXT_SET_DUMP_ROUTES).matches(mi, false)) {
                    mi = mi.withComments(Collections.singletonList(RecipesUtil.createMultinlineComment(
                            " Method '" + mi.getSimpleName() + "' accepts String parameter ('xml' or 'yaml' or 'false'). ")));
                }
                //Boolean isDumpRoutes(); -> getDumpRoutes(); with returned type String
                else if (getMethodMatcher(M_CONTEXT_IS_DUMP_ROUTES).matches(mi, false)) {
                    mi = mi.withName(mi.getName().withSimpleName("getDumpRoutes"))
                            .withComments(Collections.singletonList(RecipesUtil.createMultinlineComment(
                                    " Method 'getDumpRoutes' returns String value ('xml' or 'yaml' or 'false'). ")));
                }
                // (CamelRuntimeCatalog) context.getExtension(RuntimeCamelCatalog.class) -> context.getCamelContextExtension().getContextPlugin(RuntimeCamelCatalog.class);
                else if (getMethodMatcher(MATCHER_CONTEXT_GET_EXT).matches(mi, false)) {

                    mi = mi.withName(mi.getName().withSimpleName("getCamelContextExtension().getContextPlugin"))
                            .withMethodType(mi.getMethodType());
                    //remove type cast before expression
                    if (getCursor().getParent().getValue() instanceof J.TypeCast
                            && ((J.TypeCast) getCursor().getParent().getValue()).getType().equals(mi.getType())) {
                        getCursor().getParent().putMessage("remove_type_cast", mi);
                    }

                }
                return mi;
            }

            @Override
            public @Nullable J postVisit(J tree, ExecutionContext context) {
                J j = super.postVisit(tree, context);

                UUID adaptCast = getCursor().getMessage("adapt_cast");

                if (adaptCast != null) {
                    J.MethodInvocation mi = (J.MethodInvocation) j;
                    J.ControlParentheses<?> cp = (J.ControlParentheses<?>) adaptCache.get(adaptCast);

                    J.MethodInvocation m = mi.withSelect(cp);
                    return m;
                }

                J removeTypeCast = getCursor().getMessage("remove_type_cast");

                if (removeTypeCast != null) {
                    return removeTypeCast;
                }

                return j;
            }

        });
    }

}
