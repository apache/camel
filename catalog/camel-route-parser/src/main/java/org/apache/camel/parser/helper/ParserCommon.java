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

package org.apache.camel.parser.helper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;

import org.apache.camel.parser.roaster.StatementFieldSource;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ASTNode;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Block;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Expression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.FieldDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MemberValuePair;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.NormalAnnotation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.NumberLiteral;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.SimpleName;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Type;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;

public final class ParserCommon {

    private ParserCommon() {

    }

    public static Expression evalExpression(Expression exp) {
        if (exp instanceof SingleMemberAnnotation singleMemberAnnotation) {
            exp = singleMemberAnnotation.getValue();
        } else if (exp instanceof NormalAnnotation normalAnnotation) {
            List<?> values = normalAnnotation.values();
            for (Object value : values) {
                MemberValuePair pair = (MemberValuePair) value;
                if ("uri".equals(pair.getName().toString())) {
                    exp = pair.getValue();
                    break;
                }
            }
        }
        return exp;
    }

    static FieldSource<JavaClassSource> findFieldInBlock(JavaClassSource clazz, Block block, String fieldName) {
        for (Object statement : block.statements()) {
            // try local statements first in the block
            if (statement instanceof VariableDeclarationStatement variableDeclarationStatement) {
                final Type type = variableDeclarationStatement.getType();
                for (Object obj : variableDeclarationStatement.fragments()) {
                    if (obj instanceof VariableDeclarationFragment fragment) {
                        SimpleName name = fragment.getName();
                        if (name != null && fieldName.equals(name.getIdentifier())) {
                            return new StatementFieldSource<>(clazz, fragment, type);
                        }
                    }
                }
            }

            // okay the field may be buried inside an anonymous inner class as a field declaration
            // outside the configure method, so lets go back to the parent and see what we can find
            ASTNode node = block.getParent();
            if (node instanceof MethodDeclaration) {
                node = node.getParent();
            }
            if (node instanceof AnonymousClassDeclaration) {
                List<?> declarations = ((AnonymousClassDeclaration) node).bodyDeclarations();
                for (Object dec : declarations) {
                    if (dec instanceof FieldDeclaration fd) {
                        final Type type = fd.getType();
                        for (Object obj : fd.fragments()) {
                            if (obj instanceof VariableDeclarationFragment fragment) {
                                SimpleName name = fragment.getName();
                                if (name != null && fieldName.equals(name.getIdentifier())) {
                                    return new StatementFieldSource(clazz, fragment, type);
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    static FieldSource<JavaClassSource> getField(JavaClassSource clazz, Block block, SimpleName ref) {
        String fieldName = ref.getIdentifier();
        if (fieldName != null) {
            // find field in class
            FieldSource<JavaClassSource> field = clazz != null ? clazz.getField(fieldName) : null;
            if (field == null) {
                field = findFieldInBlock(clazz, block, fieldName);
            }
            return field;
        }
        return null;
    }

    public static boolean isNumericOperator(JavaClassSource clazz, Block block, Expression expression) {
        if (expression instanceof NumberLiteral) {
            return true;
        } else if (expression instanceof SimpleName) {
            FieldSource<JavaClassSource> field = getField(clazz, block, (SimpleName) expression);
            if (field != null) {
                return field.getType().isType("int") || field.getType().isType("long")
                        || field.getType().isType("Integer") || field.getType().isType("Long");
            }
        }
        return false;
    }

    public static boolean isCommonPredicate(String name) {
        if (name.equals("completionPredicate") || name.equals("completion")) {
            return true;
        }
        if (name.equals("onWhen") || name.equals("when") || name.equals("handled") || name.equals("continued")) {
            return true;
        }
        if (name.equals("retryWhile") || name.equals("filter") || name.equals("validate") || name.equals("loopDoWhile")) {
            return true;
        }
        return false;
    }

    static int findLineNumber(String fullyQualifiedFileName, int position) {
        int lines = 0;

        try {
            int current = 0;
            try (BufferedReader br = new BufferedReader(new FileReader(fullyQualifiedFileName))) {
                String line;
                while ((line = br.readLine()) != null) {
                    lines++;
                    current += line.length() + 1; // add 1 for line feed
                    if (current >= position) {
                        return lines;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
            return -1;
        }

        return lines;
    }
}
