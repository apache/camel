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

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.updates.AbstractCamelJavaVisitor;
import org.apache.camel.updates.RecipesUtil;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeLiteral;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class CamelBeanRecipe extends Recipe {

    private final String primitive[] = new String[] {
            "byte", "short", "int", "float", "double", "long", "char",
            "String" };

    @Override
    public String getDisplayName() {
        return "Camel bean recipe";
    }

    @Override
    public String getDescription() {
        return "Camel bean recipe.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return RecipesUtil.newVisitor(new AbstractCamelJavaVisitor() {

            @Override
            protected J.MethodInvocation doVisitMethodInvocation(J.MethodInvocation method, ExecutionContext context) {
                J.MethodInvocation mi = super.doVisitMethodInvocation(method, context);
                Pattern findMethodPattern = Pattern.compile("method=.*");

                if (mi.getSimpleName().equals("to")) {
                    List<Expression> arguments = method.getArguments();

                    for (int i = 0; i < arguments.size(); i++) {
                        Expression argument = arguments.get(i);
                        if (argument instanceof J.Literal
                                && ((J.Literal) argument).getType().getClassName().equals("java.lang.String")
                                && findMethodPattern
                                        .matcher((String) (((J.Literal) method.getArguments().get(i)).getValue()))
                                        .find()) {

                            String uriWithMethod = (String) (((J.Literal) method.getArguments().get(i)).getValue());

                            String uriWithoutMethod = uriWithMethod.split("=")[0];

                            String methodNameAndArgs = uriWithMethod.split("=")[1];

                            //method without any args, we can simply return the mi in that case.
                            if (!methodNameAndArgs.contains("(") && !methodNameAndArgs.contains(")")) {
                                return mi;
                            }

                            String methodName = extractMethodName(methodNameAndArgs);

                            String actualArgs = methodNameAndArgs.substring(
                                    methodNameAndArgs.indexOf("(") + 1,
                                    methodNameAndArgs.indexOf(")"));

                            String updatedArg = uriWithoutMethod + "=" + methodName + "(" + updateMethodArgument(actualArgs)
                                                + ")";

                            doAfterVisit(new ChangeLiteral<>(argument, p -> updatedArg));

                            return mi;

                        }

                    }

                }

                return mi;
            }

        });

    }

    private String extractMethodName(String methodCallString) {
        // Regular expression to match the method call pattern
        Pattern pattern = Pattern.compile("^([a-zA-Z_$][a-zA-Z0-9_$]*)\\(.+\\)$");
        Matcher matcher = pattern.matcher(methodCallString);

        // Check if the string matches the method call pattern
        if (matcher.matches()) {
            // Extract the method name from the matched group
            String methodName = matcher.group(1);
            return methodName;
        } else {
            // Return null if the string doesn't match the method call pattern
            return null;
        }
    }

    private String updateMethodArgument(String argument) {

        Pattern identifierPattern = Pattern.compile("^[a-zA-Z_$][a-zA-Z0-9_$]*$");
        Pattern fullyQualifiedPattern = Pattern
                .compile("^([a-zA-Z_$][a-zA-Z0-9_$]*\\.)*[a-zA-Z_$][a-zA-Z0-9_$]*$");

        String updatedArgs = Arrays.asList(argument.split(",")).stream().map(arg -> {
            if (arg.endsWith(".class")) {
                return arg;
            }

            if (Arrays.asList(primitive).contains(arg.trim())) {
                return arg + ".class";
            }

            Matcher fullyQualifiedMatcher = fullyQualifiedPattern.matcher(arg);
            if (!fullyQualifiedMatcher.matches()) {
                return arg;
            }

            String[] parts = arg.split("\\.");

            for (String part : parts) {
                Matcher identifierMatcher = identifierPattern.matcher(part);
                if (!identifierMatcher.matches()) {
                    return arg;
                }
            }

            return arg + ".class";

        }).collect(Collectors.joining(","));

        return updatedArgs;

    }

}
