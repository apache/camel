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
package org.apache.camel.updates.camel42;

import java.util.Collections;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.camel.updates.AbstractCamelJavaVisitor;
import org.apache.camel.updates.RecipesUtil;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

/**
 * Recipe migrating changes between Camel 4.3 to 4.4, for more details see the
 * <a href="https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core" >documentation</a>.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class CamelSagaRecipe extends Recipe {

    private static final String M_NEW_SAGA = "org.apache.camel.saga.InMemorySagaService newSaga()";
    private static final String M_SAGA_COORDINATOR_COMPENSATE = "org.apache.camel.saga.CamelSagaCoordinator compensate()";
    private static final String M_SAGA_COORDINATOR_COMPLETE = "org.apache.camel.saga.CamelSagaCoordinator complete()";

    @Override
    public String getDisplayName() {
        return "Camel Core changes";
    }

    @Override
    public String getDescription() {
        return "Apache Camel Core migration from version 4.0 to 4.1.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return RecipesUtil.newVisitor(new AbstractCamelJavaVisitor() {
            @Override
            protected J.MethodInvocation doVisitMethodInvocation(J.MethodInvocation method, ExecutionContext context) {
                J.MethodInvocation mi = super.doVisitMethodInvocation(method, context);

                if ((getMethodMatcher(M_NEW_SAGA).matches(mi, false)
                        || getMethodMatcher(M_SAGA_COORDINATOR_COMPENSATE).matches(mi, false)
                        || getMethodMatcher(M_SAGA_COORDINATOR_COMPLETE).matches(mi, false))
                        && RecipesUtil.methodInvocationAreArgumentEmpty(mi)) {
                    J.Identifier type
                            = RecipesUtil.createIdentifier(Space.EMPTY, "Exchange", "import org.apache.camel.Exchange");
                    J.TypeCast cp = (J.TypeCast) RecipesUtil.createTypeCast(type, RecipesUtil.createNullExpression());
                    mi = mi.withArguments(Collections.singletonList(cp.withComments(
                            Collections.singletonList(RecipesUtil.createMultinlineComment("Exchange parameter was added.")))));
                }

                return mi;
            }
        });
    }
}
