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

import org.apache.camel.updates.AbstractCamelJavaVisitor;
import org.apache.camel.updates.RecipesUtil;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AddImport;
import org.openrewrite.java.tree.J;

public class CamelEIPRecipe extends Recipe {

    @Override
    public String getDisplayName() {
        return "Replaces removed method camel EIP";
    }

    @Override
    public String getDescription() {
        return "The InOnly and InOut EIPs have been removed. Instead, use 'SetExchangePattern' or 'To' where you can specify the exchange pattern to use.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return RecipesUtil.newVisitor(new AbstractCamelJavaVisitor() {

            @Override
            protected J.MethodInvocation doVisitMethodInvocation(J.MethodInvocation method, ExecutionContext context) {
                J.MethodInvocation mi = super.doVisitMethodInvocation(method, context);

                if (mi.getSimpleName().equals("inOut") || mi.getSimpleName().equals("inOnly")) {
                    String name = mi.getSimpleName().substring(0, 1).toUpperCase() + mi.getSimpleName().substring(1);
                    mi = mi.withName(mi.getName().withSimpleName("setExchangePattern(ExchangePattern." + name + ").to"));
                    doAfterVisit(new AddImport<>("org.apache.camel.ExchangePattern", null, false));
                }
                return mi;
            }

        });

    }

}
