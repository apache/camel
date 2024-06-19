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
package org.apache.camel.updates.camel44;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.camel.updates.AbstractCamelJavaVisitor;
import org.apache.camel.updates.RecipesUtil;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

/**
 * Recipe migrating changes between Camel 4.3 to 4.4, for more details see the
 * <a href="https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core" >documentation</a>.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class CamelCoreRecipe extends Recipe {

    private static final String M_EXCHANGE_GET_CREATED = "org.apache.camel.Exchange getCreated()";
    private static final String M_PROPERTIES_LOOKUP_LOOKUP
            = "org.apache.camel.component.properties.PropertiesLookup lookup(java.lang.String)";
    private static final String M_EXPRESSION_CAUSE_JSONPATH1
            = "org.apache.camel.builder.ExpressionClause jsonpath(java.lang.String, boolean, java.lang.Class, java.lang.String)";
    private static final String M_EXPRESSION_CAUSE_JSONPATH2
            = "org.apache.camel.builder.ExpressionClause jsonpathWriteAsString(java.lang.String, boolean, java.lang.String)";
    private static final String M_EXPRESSION_CAUSE_XPATH1
            = "org.apache.camel.builder.ExpressionClause xpath(java.lang.String, java.lang.String)";
    private static final String M_EXPRESSION_CAUSE_XPATH2
            = "org.apache.camel.builder.ExpressionClause xpath(java.lang.String, java.lang.Class, java.lang.String)";
    private static final String M_EXPRESSION_CAUSE_XPATH3
            = "org.apache.camel.builder.ExpressionClause xpath(java.lang.String, java.lang.Class, org.apache.camel.support.builder.Namespaces, java.lang.String)";
    private static final String M_EXPRESSION_CAUSE_TOKENIZE1
            = "org.apache.camel.builder.ExpressionClause tokenize(java.lang.String, boolean, int, java.lang.String, boolean)";
    private static final String M_EXPRESSION_CAUSE_TOKENIZE2
            = "org.apache.camel.builder.ExpressionClause tokenize(java.lang.String, java.lang.String)";
    private static final String M_EXPRESSION_CAUSE_TOKENIZE3
            = "org.apache.camel.builder.ExpressionClause tokenize(java.lang.String, java.lang.String, boolean)";
    private static final String M_EXPRESSION_CAUSE_XQUERY1
            = "org.apache.camel.builder.ExpressionClause xquery(java.lang.String, java.lang.String)";
    private static final String M_EXPRESSION_CAUSE_XQUERY2
            = "org.apache.camel.builder.ExpressionClause xquery(java.lang.String, java.lang.Class, java.lang.String)";
    private static final String M_EXPRESSION_CAUSE_XQUERY3
            = "org.apache.camel.builder.ExpressionClause xquery(java.lang.String, java.lang.Class, boolean, java.lang.String)";
    private static final String CONST_STOP_WATCH_LONG01 = "org.apache.camel.util.StopWatch <constructor>(long)";
    private static final String CONST_STOP_WATCH_LONG02 = "org.apache.camel.util.StopWatch <constructor>(java.lang.Long)";

    @Override
    public String getDisplayName() {
        return "Camel Core changes";
    }

    @Override
    public String getDescription() {
        return "Apache Camel Core migration from version 4.3 to 4.4.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return RecipesUtil.newVisitor(new AbstractCamelJavaVisitor() {

            @Override
            protected J.MethodInvocation doVisitMethodInvocation(J.MethodInvocation method, ExecutionContext context) {
                J.MethodInvocation mi = super.doVisitMethodInvocation(method, context);

                if (getMethodMatcher(M_EXCHANGE_GET_CREATED).matches(mi, false)) {
                    //add call to getClock before call of getCreated
                    mi = mi.withName(mi.getName().withSimpleName("getClock().getCreated"));
                } else if (getMethodMatcher(M_PROPERTIES_LOOKUP_LOOKUP).matches(mi, false)
                        && mi.getArguments().size() == 1) { //without the condition, the recipes is applied again
                    //add default value null
                    List<Expression> arguments = new ArrayList<>(mi.getArguments());
                    arguments.add(RecipesUtil.createNullExpression());
                    mi = mi.withArguments(arguments);
                } else if (getMethodMatcher(M_EXPRESSION_CAUSE_JSONPATH1).matches(mi, false) ||
                        getMethodMatcher(M_EXPRESSION_CAUSE_JSONPATH2).matches(mi, false) ||
                        getMethodMatcher(M_EXPRESSION_CAUSE_TOKENIZE1).matches(mi, false) ||
                        getMethodMatcher(M_EXPRESSION_CAUSE_TOKENIZE2).matches(mi, false) ||
                        getMethodMatcher(M_EXPRESSION_CAUSE_TOKENIZE3).matches(mi, false) ||
                        getMethodMatcher(M_EXPRESSION_CAUSE_XPATH1).matches(mi, false) ||
                        getMethodMatcher(M_EXPRESSION_CAUSE_XPATH2).matches(mi, false) ||
                        getMethodMatcher(M_EXPRESSION_CAUSE_XPATH3).matches(mi, false) ||
                        getMethodMatcher(M_EXPRESSION_CAUSE_XQUERY1).matches(mi, false) ||
                        getMethodMatcher(M_EXPRESSION_CAUSE_XQUERY2).matches(mi, false) ||
                        getMethodMatcher(M_EXPRESSION_CAUSE_XQUERY3).matches(mi, false)) {
                    mi = mi.withName(
                            RecipesUtil.createIdentifier(Space.EMPTY, "removed_" + mi.getSimpleName(), mi.getType().toString()))
                            .withComments(Collections.singletonList(RecipesUtil.createMultinlineComment(
                                    "Some Java DSL for tokenize, xmlTokenize, xpath, xquery and jsonpath has been removed as part of making the DSL model consistent.\n"
                                                                                                        +
                                                                                                        "See https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core for more details.\n")));
                }

                return mi;
            }

            @Override
            protected J.NewClass doVisitNewClass(J.NewClass newClass, ExecutionContext context) {
                J.NewClass nc = super.doVisitNewClass(newClass, context);

                //can not use org.openrewrite.java.DeleteMethodArgument, because it doesn't modify calls of constructors
                if ((getMethodMatcher(CONST_STOP_WATCH_LONG01).matches(nc)
                        || getMethodMatcher(CONST_STOP_WATCH_LONG02).matches(nc))
                        && nc.getArguments().size() == 1) { //without the condition, the recipes is applied againorg.openrewrite.properties.ChangePropertyKey
                    nc = nc.withArguments(Collections.emptyList()).withComments(Collections.singletonList(RecipesUtil
                            .createMultinlineComment(
                                    "Removed the deprecated constructor from the internal class org.apache.camel.util.StopWatch.\n"
                                                     +
                                                     "Users of this class are advised to use the default constructor if necessary.Changed exception thrown from IOException to Exception.\n")));

                }

                return nc;
            }
        });
    }
}
