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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.camel.updates.AbstractCamelJavaVisitor;
import org.apache.camel.updates.RecipesUtil;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

@EqualsAndHashCode(callSuper = true)
@Value
public class CamelHttpRecipe extends Recipe {

    private static final String SET_CREDENTIALS = "org.apache.http.impl.client.BasicCredentialsProvider setCredentials(..)";
    private static final String SCOPE_ANY = "AuthScope.ANY";

    @Override
    public String getDisplayName() {
        return "Camel Http Extension changes";
    }

    @Override
    public String getDescription() {
        return "Camel Http Extension changes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return RecipesUtil.newVisitor("org.apache.http..*", new AbstractCamelJavaVisitor() {
            @Override
            protected J.Import doVisitImport(J.Import _import, ExecutionContext context) {
                doAfterVisit(
                        new ChangeType(
                                "org.apache.http.HttpHost",
                                "org.apache.hc.core5.http.HttpHost", true).getVisitor());
                doAfterVisit(
                        new ChangeType(
                                "org.apache.http.client.protocol.HttpClientContext",
                                "org.apache.hc.client5.http.protocol.HttpClientContext", true).getVisitor());
                doAfterVisit(
                        new ChangeType(
                                "org.apache.http.protocol.HttpContext",
                                "org.apache.hc.core5.http.protocol.HttpContext", true).getVisitor());
                doAfterVisit(
                        new ChangeType(
                                "org.apache.http.impl.auth.BasicScheme",
                                "org.apache.hc.client5.http.impl.auth.BasicScheme", true).getVisitor());
                doAfterVisit(
                        new ChangeType(
                                "org.apache.http.impl.client.BasicAuthCache",
                                "org.apache.hc.client5.http.impl.auth.BasicAuthCache", true).getVisitor());
                doAfterVisit(
                        new ChangeType(
                                "org.apache.http.impl.client.BasicCredentialsProvider",
                                "org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider", true).getVisitor());
                doAfterVisit(
                        new ChangeType(
                                "org.apache.http.auth.AuthScope",
                                "org.apache.hc.client5.http.auth.AuthScope", true).getVisitor());
                doAfterVisit(
                        new ChangeType(
                                "org.apache.http.auth.UsernamePasswordCredentials",
                                "org.apache.hc.client5.http.auth.UsernamePasswordCredentials", true).getVisitor());
                doAfterVisit(
                        new ChangeType(
                                "org.apache.http.conn.ssl.NoopHostnameVerifier",
                                "org.apache.hc.client5.http.conn.ssl.NoopHostnameVerifier", true).getVisitor());

                return super.doVisitImport(_import, context);
            }

            @Override
            protected J.FieldAccess doVisitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext context) {
                J.FieldAccess f = super.doVisitFieldAccess(fieldAccess, context);

                //The component has been upgraded to use Apache HttpComponents v5
                //AuthScope.ANY -> new AuthScope(null, -1)
                if ("ANY".equals(f.getSimpleName()) && "org.apache.http.auth.AuthScope".equals(f.getType().toString())) {
                    JavaTemplate.Builder templateBuilder = JavaTemplate.builder("new AuthScope(null, -1)");
                    J.NewClass nc = templateBuilder.build().apply(updateCursor(fieldAccess),
                            f.getCoordinates().replace())
                            .withPrefix(f.getPrefix());
                    getCursor().putMessage("authScopeNewClass", nc);
                }
                return f;
            }

            @Override
            public @Nullable J postVisit(J tree, ExecutionContext context) {
                J j = super.postVisit(tree, context);

                //use a new class instead of original element
                J.NewClass newClass = getCursor().getMessage("authScopeNewClass");
                if (newClass != null) {
                    maybeAddImport("org.apache.hc.client5.http.auth.AuthScope", null, false);
                    return newClass;
                }

                return j;
            }

        });
    }

}
