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
package org.apache.camel.updates;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parent of Camel xml visitors, catches any exception, logs it and then continues.
 * <p>
 * Every method <i>visit*</i> is marked as final and methods <i>doVisit*</i> are used instead.
 * </p>
 * <p>
 * Simple cache for methodMatchers is implemented here. Usage: call <i>MethodMatcher getMethodMatcher(String
 * signature)</i>.
 * </p>
 */
public abstract class AbstractCamelJavaVisitor extends JavaIsoVisitor<ExecutionContext> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCamelJavaVisitor.class);

    //There is no need to  initialize all patterns at the class start.
    //Map is a cache for created patterns
    private static final Map<String, MethodMatcher> methodMatchers = new HashMap<>();

    @Override
    public final J.Import visitImport(J.Import _import, ExecutionContext context) {

        return executeVisitWithCatch(() -> doVisitImport(_import, context), _import, context);
    }

    @Override
    public final J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext context) {
        return executeVisitWithCatch(() -> doVisitClassDeclaration(classDecl, context), classDecl, context);
    }

    @Override
    public final J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext context) {
        return executeVisitWithCatch(() -> doVisitFieldAccess(fieldAccess, context), fieldAccess, context);
    }

    @Override
    public final J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext context) {
        return executeVisitWithCatch(() -> doVisitMethodDeclaration(method, context), method, context);
    }

    @Override
    public final J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext context) {
        return executeVisitWithCatch(() -> doVisitMethodInvocation(method, context), method, context);
    }

    @Override
    public final J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext context) {
        return executeVisitWithCatch(() -> doVisitAnnotation(annotation, context), annotation, context);
    }

    @Override
    public final J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext context) {
        return executeVisitWithCatch(() -> doVisitNewClass(newClass, context), newClass, context);
    }

    //-------------------------------- internal methods used by children---------------------------------

    protected J.Import doVisitImport(J.Import _import, ExecutionContext context) {
        return super.visitImport(_import, context);
    }

    protected J.ClassDeclaration doVisitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext context) {
        return super.visitClassDeclaration(classDecl, context);
    }

    protected J.FieldAccess doVisitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext context) {
        return super.visitFieldAccess(fieldAccess, context);
    }

    protected J.MethodDeclaration doVisitMethodDeclaration(J.MethodDeclaration method, ExecutionContext context) {
        return super.visitMethodDeclaration(method, context);
    }

    protected J.MethodInvocation doVisitMethodInvocation(J.MethodInvocation method, ExecutionContext context) {
        return super.visitMethodInvocation(method, context);
    }

    protected J.Annotation doVisitAnnotation(J.Annotation annotation, ExecutionContext context) {
        return super.visitAnnotation(annotation, context);
    }

    protected J.NewClass doVisitNewClass(J.NewClass newClass, ExecutionContext context) {
        return super.visitNewClass(newClass, context);
    }

    // ------------------------------------------ helper methods -------------------------------------------

    // If the migration fails - do not fail whole migration process, only this one recipe
    protected <T extends J> T executeVisitWithCatch(Supplier<T> visitMethod, T origValue, ExecutionContext context) {
        try {
            return visitMethod.get();
        } catch (Exception e) {
            LOGGER.warn(String.format("Internal error detected in %s, recipe is skipped.", getClass().getName()), e);
            return origValue;
        }
    }

    protected MethodMatcher getMethodMatcher(String signature) {
        synchronized (methodMatchers) {
            MethodMatcher matcher = methodMatchers.get(signature);

            if (matcher == null) {
                matcher = new MethodMatcher(signature);
                methodMatchers.put(signature, matcher);
            }

            return matcher;
        }
    }
}
