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
package org.apache.camel.updates.camel43;

import java.util.Collections;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.camel.updates.AbstractCamelJavaVisitor;
import org.apache.camel.updates.RecipesUtil;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;

/**
 * Recipe migrating changes between Camel 4.3 to 4.4, for more details see the
 * <a href="https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core" >documentation</a>.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class CamelThrottleEIPRecipe extends Recipe {

    private static final String M_THROTTLE_PRIMITIVE = "org.apache.camel.model.ProcessorDefinition throttle(long)";
    private static final String M_THROTTLE_TIME_PERIOD_MILLIS_PRIMITIVE
            = "org.apache.camel.model.ThrottleDefinition timePeriodMillis(long)";
    private static String WARNING_COMMENT
            = " Throttle now uses the number of concurrent requests as the throttling measure instead of the number of requests per period.";

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

                if (getMethodMatcher(M_THROTTLE_PRIMITIVE).matches(mi, false)
                        && !RecipesUtil.isCommentBeforeElement(mi, WARNING_COMMENT)) {
                    mi = mi.withComments(Collections.singletonList(RecipesUtil.createMultinlineComment(WARNING_COMMENT)));
                    getCursor().putMessage("throttle-migrated", true);
                } else if (getMethodMatcher(M_THROTTLE_TIME_PERIOD_MILLIS_PRIMITIVE).matches(mi, false)) {
                    if (mi.getSelect() instanceof J.MethodInvocation) {
                        return (J.MethodInvocation) mi.getSelect();
                    } else {
                        return null;
                    }
                }

                return mi;
            }
        });
    }
}
