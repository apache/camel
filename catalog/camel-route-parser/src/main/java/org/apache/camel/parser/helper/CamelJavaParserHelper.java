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

import org.apache.camel.parser.ParserResult;
import org.apache.camel.parser.RouteBuilderParser;
import org.apache.camel.parser.roaster.AnonymousMethodSource;
import org.apache.camel.tooling.util.Strings;
import org.apache.camel.util.URISupport;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ASTNode;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Block;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.BooleanLiteral;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Expression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ExpressionStatement;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.InfixExpression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodInvocation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.NumberLiteral;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.QualifiedName;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ReturnStatement;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.SimpleName;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.SimpleType;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Statement;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.StringLiteral;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.TextBlock;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.VariableDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.jboss.forge.roaster.model.Annotation;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;

/**
 * A Camel Java parser that only depends on the Roaster API.
 * <p/>
 * This implementation is lower level details. For a higher level parser see {@link RouteBuilderParser}.
 */
public final class CamelJavaParserHelper {

    private CamelJavaParserHelper() {
        // utility class
    }

    public static MethodSource<JavaClassSource> findConfigureMethod(JavaClassSource clazz) {
        MethodSource<JavaClassSource> method = clazz.getMethod("configure");
        // must be public void configure()
        if (method != null && method.isPublic() && method.getParameters().isEmpty() && method.getReturnType().isType("void")) {
            return method;
        }

        // maybe the route builder is from unit testing with camel-test as an anonymous inner class
        // there is a bit of code to dig out this using the eclipse jdt api
        method = findCreateRouteBuilderMethod(clazz);
        if (method != null) {
            return findConfigureMethodInCreateRouteBuilder(clazz, method);
        }

        return null;
    }

    public static List<MethodSource<JavaClassSource>> findInlinedConfigureMethods(JavaClassSource clazz) {
        List<MethodSource<JavaClassSource>> answer = new ArrayList<>();

        List<MethodSource<JavaClassSource>> methods = clazz.getMethods();
        if (methods != null) {
            for (MethodSource<JavaClassSource> method : methods) {
                if (method.isPublic()
                        && (method.getParameters() == null || method.getParameters().isEmpty())
                        && (method.getReturnType() == null || method.getReturnType().isType("void"))) {
                    // maybe the method contains an inlined createRouteBuilder usually from an unit test method
                    MethodSource<JavaClassSource> builder = findConfigureMethodInCreateRouteBuilder(clazz, method);
                    if (builder != null) {
                        answer.add(builder);
                    }
                }
            }
        }

        return answer;
    }

    private static MethodSource<JavaClassSource> findCreateRouteBuilderMethod(JavaClassSource clazz) {
        MethodSource<JavaClassSource> method = clazz.getMethod("createRouteBuilder");
        if (method != null && (method.isPublic() || method.isProtected()) && method.getParameters().isEmpty()) {
            return method;
        }
        return null;
    }

    private static MethodSource<JavaClassSource> findConfigureMethodInCreateRouteBuilder(
            JavaClassSource clazz, MethodSource<JavaClassSource> method) {
        // find configure inside the code
        MethodDeclaration md = (MethodDeclaration) method.getInternal();
        Block block = md.getBody();
        if (block != null) {
            List<?> statements = block.statements();
            for (Object statement : statements) {
                Statement stmt = (Statement) statement;
                Expression exp = null;
                if (stmt instanceof ReturnStatement rs) {
                    exp = rs.getExpression();
                } else if (stmt instanceof ExpressionStatement es) {
                    exp = es.getExpression();
                    if (exp instanceof MethodInvocation mi) {
                        for (Object arg : mi.arguments()) {
                            if (arg instanceof ClassInstanceCreation) {
                                exp = (Expression) arg;
                                break;
                            }
                        }
                    }
                }
                if (exp instanceof ClassInstanceCreation cic) {
                    boolean isRouteBuilder = false;
                    if (cic.getType() instanceof SimpleType st) {
                        isRouteBuilder = "RouteBuilder".equals(st.getName().toString());
                    }
                    if (isRouteBuilder && cic.getAnonymousClassDeclaration() != null) {
                        List<?> body = cic.getAnonymousClassDeclaration().bodyDeclarations();
                        for (Object line : body) {
                            if (line instanceof MethodDeclaration amd) {
                                if ("configure".equals(amd.getName().toString())) {
                                    return new AnonymousMethodSource(clazz, amd);
                                }
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    public static List<ParserResult> parseCamelRouteIds(MethodSource<JavaClassSource> method) {
        return doParseCamelUris(method, true, false, true, false, true);
    }

    public static List<ParserResult> parseCamelConsumerUris(
            MethodSource<JavaClassSource> method, boolean strings, boolean fields) {
        return doParseCamelUris(method, true, false, strings, fields, false);
    }

    public static List<ParserResult> parseCamelProducerUris(
            MethodSource<JavaClassSource> method, boolean strings, boolean fields) {
        return doParseCamelUris(method, false, true, strings, fields, false);
    }

    private static List<ParserResult> doParseCamelUris(
            MethodSource<JavaClassSource> method, boolean consumers, boolean producers,
            boolean strings, boolean fields, boolean routeIdsOnly) {

        List<ParserResult> answer = new ArrayList<>();

        if (method != null) {
            MethodDeclaration md = (MethodDeclaration) method.getInternal();
            Block block = md.getBody();
            if (block != null) {
                for (Object statement : md.getBody().statements()) {
                    // must be a method call expression
                    if (statement instanceof ExpressionStatement es) {
                        Expression exp = es.getExpression();

                        List<ParserResult> uris = new ArrayList<>();
                        parseExpression(method.getOrigin(), block, exp, uris, consumers, producers, strings, fields,
                                routeIdsOnly);
                        if (!uris.isEmpty()) {
                            // reverse the order as we will grab them from last->first
                            Collections.reverse(uris);
                            answer.addAll(uris);
                        }
                    }
                }
            }
        }

        return answer;
    }

    private static void parseExpression(
            JavaClassSource clazz, Block block, Expression exp, List<ParserResult> uris,
            boolean consumers, boolean producers, boolean strings, boolean fields, boolean routeIdsOnly) {
        if (exp == null) {
            return;
        }
        if (exp instanceof MethodInvocation mi) {
            doParseCamelUris(clazz, block, mi, uris, consumers, producers, strings, fields, routeIdsOnly);
            // if the method was called on another method, then recursive
            exp = mi.getExpression();
            parseExpression(clazz, block, exp, uris, consumers, producers, strings, fields, routeIdsOnly);
        }
    }

    private static void doParseCamelUris(
            JavaClassSource clazz, Block block, MethodInvocation mi, List<ParserResult> uris,
            boolean consumers, boolean producers, boolean strings, boolean fields, boolean routeIdsOnly) {
        String name = mi.getName().getIdentifier();

        if (routeIdsOnly) {
            // include route id for consumers
            if ("routeId".equals(name)) {
                List<?> args = mi.arguments();
                if (args != null) {
                    for (Object arg : args) {
                        if (isValidArgument(arg)) {
                            String routeId = getLiteralValue(clazz, block, (Expression) arg);
                            if (!Strings.isNullOrEmpty(routeId)) {
                                int position = ((Expression) arg).getStartPosition();
                                int len = ((Expression) arg).getLength();
                                uris.add(new ParserResult(name, position, len, routeId));
                            }
                        }
                    }
                }
            }
            // we only want route ids so return here
            return;
        }

        if (consumers) {
            if ("from".equals(name)) {
                List<?> args = mi.arguments();
                if (args != null) {
                    iterateOverArguments(clazz, block, uris, strings, fields, args, name);
                }
            }
            if ("fromF".equals(name)) {
                parseFirstArgument(clazz, block, mi, uris, strings, fields, name);
            }
            if ("interceptFrom".equals(name)) {
                parseFirstArgument(clazz, block, mi, uris, strings, fields, name);
            }
            if ("pollEnrich".equals(name)) {
                parseFirstArgument(clazz, block, mi, uris, strings, fields, name);
            }
        }

        if (producers) {
            if ("to".equals(name) || "toD".equals(name)) {
                List<?> args = mi.arguments();
                if (args != null) {
                    iterateOverArguments(clazz, block, uris, strings, fields, args, name);
                }
            }
            if ("toF".equals(name)) {
                parseFirstArgument(clazz, block, mi, uris, strings, fields, name);
            }
            if ("enrich".equals(name) || "wireTap".equals(name)) {
                parseFirstArgument(clazz, block, mi, uris, strings, fields, name);
            }
        }
    }

    private static void parseFirstArgument(
            JavaClassSource clazz, Block block, MethodInvocation mi, List<ParserResult> uris, boolean strings, boolean fields,
            String name) {
        List<?> args = mi.arguments();
        // the first argument is where the uri is
        if (args != null && !args.isEmpty()) {
            parseFirstArgument(clazz, block, uris, strings, fields, args, name);
        }
    }

    private static void parseFirstArgument(
            JavaClassSource clazz, Block block, List<ParserResult> uris, boolean strings, boolean fields, List<?> args,
            String name) {
        Object arg = args.get(0);
        if (isValidArgument(arg)) {
            extractEndpointUriFromArgument(name, clazz, block, uris, arg, strings, fields);
        }
    }

    private static void iterateOverArguments(
            JavaClassSource clazz, Block block, List<ParserResult> uris, boolean strings, boolean fields, List<?> args,
            String name) {
        for (Object arg : args) {
            if (isValidArgument(arg)) {
                extractEndpointUriFromArgument(name, clazz, block, uris, arg, strings, fields);
            }
        }
    }

    private static boolean isValidArgument(Object arg) {
        // skip boolean argument, as toD can accept a boolean value
        if (arg instanceof BooleanLiteral) {
            return false;
        }
        // skip ExchangePattern argument
        if (arg instanceof QualifiedName qn) {
            String name = qn.getFullyQualifiedName();
            if (name.startsWith("ExchangePattern")) {
                return false;
            }
        }
        return true;
    }

    private static void extractEndpointUriFromArgument(
            String node, JavaClassSource clazz, Block block, List<ParserResult> uris, Object arg, boolean strings,
            boolean fields) {
        if (strings) {
            String uri = getLiteralValue(clazz, block, (Expression) arg);
            // java 17 text block
            uri = URISupport.textBlockToSingleLine(uri);
            if (!Strings.isNullOrEmpty(uri)) {
                int position = ((Expression) arg).getStartPosition();
                int len = ((Expression) arg).getLength();

                // if the node is fromF or toF, then replace all %X with {{%X}} as we cannot parse that value
                if ("fromF".equals(node) || "toF".equals(node)) {
                    uri = uri.replace("%s", "{{%s}}");
                    uri = uri.replace("%d", "{{%d}}");
                    uri = uri.replace("%b", "{{%b}}");
                }

                uris.add(new ParserResult(node, position, len, uri));
                return;
            }
        }
        if (fields && arg instanceof SimpleName) {
            FieldSource<JavaClassSource> field = ParserCommon.getField(clazz, block, (SimpleName) arg);
            if (field != null) {
                // find the endpoint uri from the annotation
                AnnotationSource<JavaClassSource> annotation = field.getAnnotation("org.apache.camel.cdi.Uri");
                if (annotation == null) {
                    annotation = field.getAnnotation("org.apache.camel.EndpointInject");
                }
                if (annotation != null) {
                    Expression exp = extractExpression(annotation.getInternal());
                    String uri = CamelJavaParserHelper.getLiteralValue(clazz, block, exp);
                    if (!Strings.isNullOrEmpty(uri)) {
                        int position = ((SimpleName) arg).getStartPosition();
                        int len = ((SimpleName) arg).getLength();
                        uris.add(new ParserResult(node, position, len, uri));
                    }
                } else {
                    // the field may be initialized using variables, so we need to evaluate those expressions
                    Object fi = field.getInternal();
                    if (fi instanceof VariableDeclaration) {
                        Expression exp = ((VariableDeclaration) fi).getInitializer();
                        String uri = CamelJavaParserHelper.getLiteralValue(clazz, block, exp);
                        if (!Strings.isNullOrEmpty(uri)) {
                            // we want the position of the field, and not in the route
                            int position = ((VariableDeclaration) fi).getStartPosition();
                            int len = ((VariableDeclaration) fi).getLength();
                            uris.add(new ParserResult(node, position, len, uri));
                        }
                    }
                }
            }
        }

        // cannot parse it so add a failure
        uris.add(new ParserResult(node, -1, -1, arg.toString(), false));
    }

    private static Expression extractExpression(Object annotation) {
        Expression exp = (Expression) annotation;
        return ParserCommon.evalExpression(exp);
    }

    public static List<ParserResult> parseCamelLanguageExpressions(MethodSource<JavaClassSource> method, String language) {
        List<ParserResult> answer = new ArrayList<>();

        MethodDeclaration md = (MethodDeclaration) method.getInternal();
        Block block = md.getBody();
        if (block != null) {
            for (Object statement : block.statements()) {
                // must be a method call expression
                if (statement instanceof ExpressionStatement es) {
                    Expression exp = es.getExpression();

                    List<ParserResult> expressions = new ArrayList<>();
                    parseExpression(null, method.getOrigin(), block, exp, expressions, language);
                    if (!expressions.isEmpty()) {
                        // reverse the order as we will grab them from last->first
                        Collections.reverse(expressions);
                        answer.addAll(expressions);
                    }
                }
            }
        }

        return answer;
    }

    private static void parseExpression(
            String node, JavaClassSource clazz, Block block, Expression exp, List<ParserResult> expressions, String language) {
        if (exp == null) {
            return;
        }
        if (exp instanceof MethodInvocation mi) {
            doParseCamelLanguage(node, clazz, block, mi, expressions, language);
            // if the method was called on another method, then recursive
            exp = mi.getExpression();
            parseExpression(node, clazz, block, exp, expressions, language);
        }
    }

    private static void doParseCamelLanguage(
            String node, JavaClassSource clazz, Block block, MethodInvocation mi, List<ParserResult> expressions,
            String language) {
        String name = mi.getName().getIdentifier();

        if (language.equals(name)) {
            List<?> args = mi.arguments();
            // the first argument is a string parameter for the language expression
            if (args != null && !args.isEmpty()) {
                // it is a String type
                Object arg = args.get(0);
                String exp = getLiteralValue(clazz, block, (Expression) arg);
                if (!Strings.isNullOrEmpty(exp)) {
                    // is this a expression that is called as a predicate or expression
                    boolean predicate = false;
                    Expression parent = mi.getExpression();
                    if (parent == null) {
                        // maybe it's an argument
                        List<?> list = mi.arguments();
                        // must be a single argument
                        if (list != null && list.size() == 1) {
                            ASTNode o = (ASTNode) list.get(0);
                            ASTNode p = o.getParent();
                            if (p instanceof MethodInvocation) {
                                String pName = ((MethodInvocation) p).getName().getIdentifier();
                                if (language.equals(pName)) {
                                    // okay find the parent of the language which is the method that uses the language
                                    parent = (Expression) p.getParent();
                                }
                            }
                        }
                    }
                    if (parent instanceof MethodInvocation emi) {
                        String parentName = emi.getName().getIdentifier();
                        predicate = isLanguagePredicate(parentName);
                    }

                    int position = ((Expression) arg).getStartPosition();
                    int len = ((Expression) arg).getLength();
                    ParserResult result = new ParserResult(node, position, len, exp);
                    result.setPredicate(predicate);
                    expressions.add(result);
                }
            }
        }

        // the language maybe be passed in as an argument
        List<?> args = mi.arguments();
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof MethodInvocation ami) {
                    doParseCamelLanguage(node, clazz, block, ami, expressions, language);
                }
            }
        }
    }

    /**
     * Using language expressions in the Java DSL may be used in certain places as predicate only
     */
    private static boolean isLanguagePredicate(String name) {
        if (name == null) {
            return false;
        }
        return ParserCommon.isCommonPredicate(name);
    }

    public static String getLiteralValue(JavaClassSource clazz, Block block, Expression expression) {
        // unwrap parenthesis
        if (expression instanceof ParenthesizedExpression) {
            expression = ((ParenthesizedExpression) expression).getExpression();
        }

        if (expression instanceof StringLiteral stringLiteral) {
            return stringLiteral.getLiteralValue();
        } else if (expression instanceof BooleanLiteral booleanLiteral) {
            return String.valueOf(booleanLiteral.booleanValue());
        } else if (expression instanceof NumberLiteral numberLiteral) {
            return numberLiteral.getToken();
        } else if (expression instanceof TextBlock textBlock) {
            return textBlock.getLiteralValue();
        }

        // if it's a method invocation then add a dummy value assuming the method invocation will return a valid response
        if (expression instanceof MethodInvocation methodInvocation) {
            String name = methodInvocation.getName().getIdentifier();
            return "{{" + name + "}}";
        }

        // if it's a qualified name (usually a constant field in another class)
        // then add a dummy value as we cannot find the field value in other classes and maybe even outside the
        // source code we have access to
        if (expression instanceof QualifiedName qn) {
            String name = qn.getFullyQualifiedName();
            return "{{" + name + "}}";
        }

        if (expression instanceof SimpleName) {
            FieldSource<JavaClassSource> field = ParserCommon.getField(clazz, block, (SimpleName) expression);
            if (field != null) {
                // is the field annotated with a Camel endpoint
                if (field.getAnnotations() != null) {
                    for (Annotation<JavaClassSource> ann : field.getAnnotations()) {
                        boolean valid = "org.apache.camel.EndpointInject".equals(ann.getQualifiedName())
                                || "org.apache.camel.cdi.Uri".equals(ann.getQualifiedName());
                        if (valid) {
                            Expression exp = extractExpression(ann.getInternal());
                            if (exp != null) {
                                return getLiteralValue(clazz, block, exp);
                            }
                        }
                    }
                }
                // is the field an org.apache.camel.Endpoint type?
                return endpointTypeCheck(clazz, block, field);
            } else {
                // we could not find the field in this class/method, so its maybe from some other super class, so insert a dummy value
                final String fieldName = ((SimpleName) expression).getIdentifier();
                return "{{" + fieldName + "}}";
            }
        } else if (expression instanceof InfixExpression ie) {
            return getValueFromExpression(clazz, block, ie);
        }
        return null;
    }

    private static String getValueFromExpression(JavaClassSource clazz, Block block, InfixExpression ie) {
        String answer = null;
        // is it a string that is concat together?
        if (InfixExpression.Operator.PLUS.equals(ie.getOperator())) {

            String val1 = getLiteralValue(clazz, block, ie.getLeftOperand());
            String val2 = getLiteralValue(clazz, block, ie.getRightOperand());

            // if numeric then we plus the values, otherwise we string concat
            boolean numeric = ParserCommon.isNumericOperator(clazz, block, ie.getLeftOperand())
                    && ParserCommon.isNumericOperator(clazz, block, ie.getRightOperand());
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

    static String endpointTypeCheck(JavaClassSource clazz, Block block, FieldSource<JavaClassSource> field) {
        Expression expression;
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
        return null;
    }

}
