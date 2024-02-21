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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.camel.parser.model.RestConfigurationDetails;
import org.apache.camel.parser.model.RestServiceDetails;
import org.apache.camel.parser.model.RestVerbDetails;
import org.apache.camel.parser.roaster.StatementFieldSource;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ASTNode;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Block;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.BooleanLiteral;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Expression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ExpressionStatement;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.FieldDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.InfixExpression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodInvocation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.NumberLiteral;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.QualifiedName;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.SimpleName;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.StringLiteral;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.TextBlock;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Type;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.jboss.forge.roaster.model.Annotation;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;

import static org.apache.camel.parser.helper.ParserCommon.findLineNumber;

/**
 * A Camel Java Rest DSL parser that only depends on the Roaster API.
 * <p/>
 * This implement is used for parsing the Camel routes and build a tree structure of the Rest DSL services.
 */
public final class CamelJavaRestDslParserHelper {

    public List<RestConfigurationDetails> parseRestConfiguration(
            JavaClassSource clazz, String fullyQualifiedFileName,
            MethodSource<JavaClassSource> configureMethod) {

        List<RestConfigurationDetails> answer = new ArrayList<>();

        if (configureMethod != null) {
            MethodDeclaration md = (MethodDeclaration) configureMethod.getInternal();
            Block block = md.getBody();
            if (block != null) {
                for (Object statement : md.getBody().statements()) {
                    // must be a method call expression
                    if (statement instanceof ExpressionStatement es) {
                        Expression exp = es.getExpression();
                        boolean valid = isRestConfiguration(exp);
                        if (valid) {
                            RestConfigurationDetails node = new RestConfigurationDetails();
                            answer.add(node);

                            // include source code details
                            int pos = exp.getStartPosition();
                            int line = findLineNumber(fullyQualifiedFileName, pos);
                            if (line > -1) {
                                node.setLineNumber(Integer.toString(line));
                            }
                            pos = exp.getStartPosition() + exp.getLength();
                            line = findLineNumber(fullyQualifiedFileName, pos);
                            if (line > -1) {
                                node.setLineNumberEnd(Integer.toString(line));
                            }
                            node.setFileName(fullyQualifiedFileName);
                            node.setClassName(clazz.getQualifiedName());
                            node.setMethodName(configureMethod.getName());

                            parseExpression(node, fullyQualifiedFileName, clazz, block, exp);
                        }
                    }
                }
            }
        }

        return answer;
    }

    public List<RestServiceDetails> parseRestService(
            JavaClassSource clazz, String fullyQualifiedFileName,
            MethodSource<JavaClassSource> configureMethod) {

        List<RestServiceDetails> answer = new ArrayList<>();

        if (configureMethod != null) {
            MethodDeclaration md = (MethodDeclaration) configureMethod.getInternal();
            Block block = md.getBody();
            if (block != null) {
                for (Object statement : md.getBody().statements()) {
                    // must be a method call expression
                    if (statement instanceof ExpressionStatement es) {
                        Expression exp = es.getExpression();
                        boolean valid = isRest(exp);
                        if (valid) {
                            RestServiceDetails node = new RestServiceDetails();
                            answer.add(node);

                            // include source code details
                            int pos = exp.getStartPosition();
                            int line = findLineNumber(fullyQualifiedFileName, pos);
                            if (line > -1) {
                                node.setLineNumber(Integer.toString(line));
                            }
                            pos = exp.getStartPosition() + exp.getLength();
                            line = findLineNumber(fullyQualifiedFileName, pos);
                            if (line > -1) {
                                node.setLineNumberEnd(Integer.toString(line));
                            }
                            node.setFileName(fullyQualifiedFileName);
                            node.setClassName(clazz.getQualifiedName());
                            node.setMethodName(configureMethod.getName());

                            parseExpression(node, null, fullyQualifiedFileName, clazz, block, exp);

                            // flip order of verbs as we parse bottom-up
                            if (node.getVerbs() != null) {
                                Collections.reverse(node.getVerbs());
                            }
                        }
                    }
                }
            }
        }

        return answer;
    }

    private boolean isRestConfiguration(Expression exp) {
        final String rootMethodName = findRootMethodName(exp);

        // must be from rest configuration
        return "restConfiguration".equals(rootMethodName);
    }

    private String findRootMethodName(Expression exp) {
        String rootMethodName = null;

        // find out if this is from a Camel route (e.g. from, route etc.)
        Expression sub = exp;
        while (sub instanceof MethodInvocation methodInvocation) {
            sub = methodInvocation.getExpression();
            if (sub instanceof MethodInvocation) {
                Expression parent = ((MethodInvocation) sub).getExpression();
                if (parent == null) {
                    break;
                }
            }
        }
        if (sub instanceof MethodInvocation methodInvocation) {
            rootMethodName = methodInvocation.getName().getIdentifier();
        } else if (sub instanceof SimpleName simpleName) {
            rootMethodName = simpleName.getIdentifier();
        }
        return rootMethodName;
    }

    private boolean isRest(Expression exp) {
        final String rootMethodName = findRootMethodName(exp);

        // must be from rest
        return "rest".equals(rootMethodName);
    }

    private void parseExpression(
            RestConfigurationDetails node, String fullyQualifiedFileName,
            JavaClassSource clazz, Block block,
            Expression exp) {
        if (exp == null) {
            return;
        }
        if (exp instanceof MethodInvocation mi) {
            doParseRestConfiguration(node, fullyQualifiedFileName, clazz, block, mi);
            // if the method was called on another method, then recursive
            exp = mi.getExpression();
            parseExpression(node, fullyQualifiedFileName, clazz, block, exp);
        }
    }

    private void parseExpression(
            RestServiceDetails node, RestVerbDetails verb, String fullyQualifiedFileName,
            JavaClassSource clazz, Block block,
            Expression exp) {
        if (exp == null) {
            // this rest service is not complete, if there is any details on verb then they are actually general
            // for this rest service, and we should pass the details to it
            if (verb != null) {
                node.setConsumes(verb.getConsumes());
                node.setProduces(verb.getProduces());
                node.setSkipBindingOnErrorCode(verb.getSkipBindingOnErrorCode());
                node.setClientRequestValidation(verb.getClientRequestValidation());
                node.setApiDocs(verb.getApiDocs());
                node.setDescription(verb.getDescription());
            }
            return;
        }
        if (exp instanceof MethodInvocation mi) {
            verb = doParseRestService(node, verb, fullyQualifiedFileName, clazz, block, mi);
            // if the method was called on another method, then recursive
            exp = mi.getExpression();
            parseExpression(node, verb, fullyQualifiedFileName, clazz, block, exp);
        }
    }

    private void doParseRestConfiguration(
            RestConfigurationDetails node, String fullyQualifiedFileName,
            JavaClassSource clazz, Block block,
            MethodInvocation mi) {

        // end line number is the first node in the method chain we parse
        if (node.getLineNumberEnd() == null) {
            int pos = mi.getStartPosition() + mi.getLength();
            int line = findLineNumber(fullyQualifiedFileName, pos);
            if (line > -1) {
                node.setLineNumberEnd(Integer.toString(line));
            }
        }

        String name = mi.getName().getIdentifier();
        if ("component".equals(name)) {
            node.setComponent(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("apiComponent".equals(name)) {
            node.setApiComponent(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("producerComponent".equals(name)) {
            node.setProducerComponent(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("scheme".equals(name)) {
            node.setScheme(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("host".equals(name)) {
            node.setHost(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("apiHost".equals(name)) {
            node.setApiHost(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("port".equals(name)) {
            node.setPort(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("producerApiDoc".equals(name)) {
            node.setProducerApiDoc(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("contextPath".equals(name)) {
            node.setContextPath(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("apiContextPath".equals(name)) {
            node.setApiContextPath(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("apiVendorExtension".equals(name)) {
            node.setApiVendorExtension(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("hostNameResolver".equals(name)) {
            node.setHostNameResolver(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("bindingMode".equals(name)) {
            node.setBindingMode(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("skipBindingOnErrorCode".equals(name)) {
            node.setSkipBindingOnErrorCode(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("clientRequestValidation".equals(name)) {
            node.setClientRequestValidation(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("enableCORS".equals(name)) {
            node.setEnableCORS(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("jsonDataFormat".equals(name)) {
            node.setJsonDataFormat(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("xmlDataFormat".equals(name)) {
            node.setXmlDataFormat(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("componentProperty".equals(name)) {
            String key = extractValueFromFirstArgument(clazz, block, mi);
            String value = extractValueFromSecondArgument(clazz, block, mi);
            node.addComponentProperty(key, value);
        } else if ("endpointProperty".equals(name)) {
            String key = extractValueFromFirstArgument(clazz, block, mi);
            String value = extractValueFromSecondArgument(clazz, block, mi);
            node.addEndpointProperty(key, value);
        } else if ("consumerProperty".equals(name)) {
            String key = extractValueFromFirstArgument(clazz, block, mi);
            String value = extractValueFromSecondArgument(clazz, block, mi);
            node.addConsumerProperty(key, value);
        } else if ("dataFormatProperty".equals(name)) {
            String key = extractValueFromFirstArgument(clazz, block, mi);
            String value = extractValueFromSecondArgument(clazz, block, mi);
            node.addDataFormatProperty(key, value);
        } else if ("apiProperty".equals(name)) {
            String key = extractValueFromFirstArgument(clazz, block, mi);
            String value = extractValueFromSecondArgument(clazz, block, mi);
            node.addApiProperty(key, value);
        } else if ("corsHeaderProperty".equals(name)) {
            String key = extractValueFromFirstArgument(clazz, block, mi);
            String value = extractValueFromSecondArgument(clazz, block, mi);
            node.addCorsHeader(key, value);
        }
    }

    private RestVerbDetails doParseRestService(
            RestServiceDetails node, RestVerbDetails verb, String fullyQualifiedFileName,
            JavaClassSource clazz, Block block,
            MethodInvocation mi) {

        // end line number is the first node in the method chain we parse
        if (node.getLineNumberEnd() == null) {
            int pos = mi.getStartPosition() + mi.getLength();
            int line = findLineNumber(fullyQualifiedFileName, pos);
            if (line > -1) {
                node.setLineNumberEnd(Integer.toString(line));
            }
        }

        String name = mi.getName().getIdentifier();
        if ("rest".equals(name)) {
            node.setPath(extractValueFromFirstArgument(clazz, block, mi));
        } else if (isParentMethod(mi, "rest")) {
            verb = doParseRestVerb(node, verb, clazz, block, mi);
        }
        return verb;
    }

    private RestVerbDetails doParseRestVerb(
            RestServiceDetails node, RestVerbDetails verb,
            JavaClassSource clazz, Block block,
            MethodInvocation mi) {
        if (verb == null) {
            verb = new RestVerbDetails();
        }

        String name = mi.getName().getIdentifier();
        if ("description".equals(name)) {
            verb.setDescription(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("bindingMode".equals(name)) {
            verb.setBindingMode(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("skipBindingOnErrorCode".equals(name)) {
            verb.setSkipBindingOnErrorCode(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("clientRequestValidation".equals(name)) {
            verb.setClientRequestValidation(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("consumes".equals(name)) {
            verb.setConsumes(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("produces".equals(name)) {
            verb.setProduces(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("type".equals(name)) {
            verb.setType(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("outType".equals(name)) {
            verb.setOutType(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("apiDocs".equals(name)) {
            verb.setApiDocs(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("to".equals(name)) {
            verb.setTo(extractValueFromFirstArgument(clazz, block, mi));
        } else if ("tag".equals(name)) {
            // tag is only available on the node
            node.setTag(extractValueFromFirstArgument(clazz, block, mi));
        }

        if ("delete".equals(name)) {
            node.addVerb(verb);
            verb.setMethod("delete");
            verb.setPath(extractValueFromFirstArgument(clazz, block, mi));
            verb = null; // reset as this verb is not complete
        } else if ("get".equals(name)) {
            node.addVerb(verb);
            verb.setMethod("get");
            verb.setPath(extractValueFromFirstArgument(clazz, block, mi));
            verb = null; // reset as this verb is not complete
        } else if ("head".equals(name)) {
            node.addVerb(verb);
            verb.setMethod("head");
            verb.setPath(extractValueFromFirstArgument(clazz, block, mi));
            verb = null; // reset as this verb is not complete
        } else if ("patch".equals(name)) {
            node.addVerb(verb);
            verb.setMethod("patch");
            verb.setPath(extractValueFromFirstArgument(clazz, block, mi));
            verb = null; // reset as this verb is not complete
        } else if ("post".equals(name)) {
            node.addVerb(verb);
            verb.setMethod("post");
            verb.setPath(extractValueFromFirstArgument(clazz, block, mi));
            verb = null; // reset as this verb is not complete
        } else if ("put".equals(name)) {
            node.addVerb(verb);
            verb.setMethod("put");
            verb.setPath(extractValueFromFirstArgument(clazz, block, mi));
            verb = null; // reset as this verb is not complete
        }

        return verb;
    }

    private static boolean isParentMethod(MethodInvocation mi, String parentName) {
        String name = mi.getName().getIdentifier();
        if (parentName.equals(name)) {
            return true;
        }

        // find out if this is from a Camel route (eg from, route etc.)
        Expression sub = mi;
        while (sub instanceof MethodInvocation) {
            sub = ((MethodInvocation) sub).getExpression();
            if (sub instanceof MethodInvocation) {
                name = ((MethodInvocation) sub).getName().getIdentifier();
                if (parentName.equals(name)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static String extractValueFromFirstArgument(JavaClassSource clazz, Block block, MethodInvocation mi) {
        List<?> args = mi.arguments();
        if (args != null && !args.isEmpty()) {
            Expression exp = (Expression) args.get(0);
            return getLiteralValue(clazz, block, exp);
        }
        return null;
    }

    private static String extractValueFromSecondArgument(JavaClassSource clazz, Block block, MethodInvocation mi) {
        List<?> args = mi.arguments();
        if (args != null && args.size() > 1) {
            Expression exp = (Expression) args.get(1);
            return getLiteralValue(clazz, block, exp);
        }
        return null;
    }

    private static FieldSource<JavaClassSource> getField(JavaClassSource clazz, Block block, SimpleName ref) {
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

    private static FieldSource<JavaClassSource> findFieldInBlock(JavaClassSource clazz, Block block, String fieldName) {
        for (Object statement : block.statements()) {
            // try local statements first in the block
            if (statement instanceof VariableDeclarationStatement) {
                final Type type = ((VariableDeclarationStatement) statement).getType();
                for (Object obj : ((VariableDeclarationStatement) statement).fragments()) {
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
            if (node instanceof AnonymousClassDeclaration anonymousClassDeclaration) {
                List<?> declarations = anonymousClassDeclaration.bodyDeclarations();
                for (Object dec : declarations) {
                    if (dec instanceof FieldDeclaration fd) {
                        final Type type = fd.getType();
                        for (Object obj : fd.fragments()) {
                            if (obj instanceof VariableDeclarationFragment fragment) {
                                SimpleName name = fragment.getName();
                                if (name != null && fieldName.equals(name.getIdentifier())) {
                                    return new StatementFieldSource<>(clazz, fragment, type);
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public static String getLiteralValue(JavaClassSource clazz, Block block, Expression expression) {
        // unwrap parenthesis
        if (expression instanceof ParenthesizedExpression) {
            expression = ((ParenthesizedExpression) expression).getExpression();
        }

        if (expression instanceof StringLiteral) {
            return ((StringLiteral) expression).getLiteralValue();
        } else if (expression instanceof BooleanLiteral) {
            return String.valueOf(((BooleanLiteral) expression).booleanValue());
        } else if (expression instanceof NumberLiteral) {
            return ((NumberLiteral) expression).getToken();
        } else if (expression instanceof TextBlock textBlock) {
            return textBlock.getLiteralValue();
        }

        // if it's a method invocation then add a dummy value assuming the method invocation will return a valid response
        if (expression instanceof MethodInvocation methodInvocation) {
            String name = methodInvocation.getName().getIdentifier();
            return "{{" + name + "}}";
        }

        // if it's a qualified name, then its an enum where we should grab the simple name
        if (expression instanceof QualifiedName qn) {
            return qn.getName().getIdentifier();
        }

        if (expression instanceof SimpleName) {
            FieldSource<JavaClassSource> field = getField(clazz, block, (SimpleName) expression);
            if (field != null) {
                // is the field annotated with a Camel endpoint
                if (field.getAnnotations() != null) {
                    for (Annotation<JavaClassSource> ann : field.getAnnotations()) {
                        boolean valid = "org.apache.camel.EndpointInject".equals(ann.getQualifiedName())
                                || "org.apache.camel.cdi.Uri".equals(ann.getQualifiedName());
                        if (valid) {
                            Expression exp = (Expression) ann.getInternal();
                            exp = ParserCommon.evalExpression(exp);

                            if (exp != null) {
                                return getLiteralValue(clazz, block, exp);
                            }
                        }
                    }
                }
                // is the field an org.apache.camel.Endpoint type?
                if ("Endpoint".equals(field.getType().getSimpleName())) {
                    // then grab the uri from the first argument
                    VariableDeclarationFragment vdf = (VariableDeclarationFragment) field.getInternal();
                    expression = vdf.getInitializer();
                    if (expression instanceof MethodInvocation mi) {
                        List<?> args = mi.arguments();
                        if (args != null && !args.isEmpty()) {
                            // the first argument has the endpoint uri
                            expression = (Expression) args.get(0);
                            return getLiteralValue(clazz, block, expression);
                        }
                    }
                } else {
                    // no annotations so try its initializer
                    VariableDeclarationFragment vdf = (VariableDeclarationFragment) field.getInternal();
                    expression = vdf.getInitializer();
                    if (expression == null) {
                        // it's a field which has no initializer, then add a dummy value assuming the field will be initialized at runtime
                        return "{{" + field.getName() + "}}";
                    } else {
                        return getLiteralValue(clazz, block, expression);
                    }
                }
            } else {
                // we could not find the field in this class/method, so its maybe from some other super class, so insert a dummy value
                final String fieldName = ((SimpleName) expression).getIdentifier();
                return "{{" + fieldName + "}}";
            }
        } else if (expression instanceof InfixExpression ie) {
            String answer = null;
            // is it a string that is concat together?
            if (InfixExpression.Operator.PLUS.equals(ie.getOperator())) {

                String val1 = getLiteralValue(clazz, block, ie.getLeftOperand());
                String val2 = getLiteralValue(clazz, block, ie.getRightOperand());

                // if numeric then we plus the values, otherwise we string concat
                boolean numeric = isNumericOperator(clazz, block, ie.getLeftOperand())
                        && isNumericOperator(clazz, block, ie.getRightOperand());
                if (numeric) {
                    long num1 = val1 != null ? Long.parseLong(val1) : 0;
                    long num2 = val2 != null ? Long.parseLong(val2) : 0;
                    answer = Long.toString(num1 + num2);
                } else {
                    answer = (val1 != null ? val1 : "") + (val2 != null ? val2 : "");
                }

                if (!answer.isEmpty()) {
                    // include extended when we concat on 2 or more lines
                    List<?> extended = ie.extendedOperands();
                    if (extended != null) {
                        StringBuilder answerBuilder = new StringBuilder(answer);
                        for (Object ext : extended) {
                            String val3 = getLiteralValue(clazz, block, (Expression) ext);
                            if (numeric) {
                                long num3 = val3 != null ? Long.parseLong(val3) : 0;
                                long num = Long.parseLong(answerBuilder.toString());
                                answerBuilder = new StringBuilder(Long.toString(num + num3));
                            } else {
                                answerBuilder.append(val3 != null ? val3 : "");
                            }
                        }
                        answer = answerBuilder.toString();
                    }
                }
            }
            return answer;
        }

        return null;
    }

    private static boolean isNumericOperator(JavaClassSource clazz, Block block, Expression expression) {
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
}
