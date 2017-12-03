/**
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
import java.util.stream.Collectors;

import org.apache.camel.parser.ParserResult;
import org.apache.camel.parser.RouteBuilderParser;
import org.apache.camel.parser.roaster.AnonymousMethodSource;
import org.apache.camel.parser.roaster.StatementFieldSource;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ASTNode;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Block;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.BooleanLiteral;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Expression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ExpressionStatement;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.FieldDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ITypeBinding;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.InfixExpression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MemberValuePair;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodInvocation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.NormalAnnotation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.NumberLiteral;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.QualifiedName;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ReturnStatement;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.SimpleName;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.SimpleType;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Statement;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.StringLiteral;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Type;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.VariableDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.jboss.forge.roaster.model.Annotation;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.util.Strings;

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
        MethodSource method = clazz.getMethod("createRouteBuilder");
        if (method != null && (method.isPublic() || method.isProtected()) && method.getParameters().isEmpty()) {
            return method;
        }
        return null;
    }

    private static MethodSource<JavaClassSource> findConfigureMethodInCreateRouteBuilder(JavaClassSource clazz, MethodSource<JavaClassSource> method) {
        // find configure inside the code
        MethodDeclaration md = (MethodDeclaration) method.getInternal();
        Block block = md.getBody();
        if (block != null) {
            List statements = block.statements();
            for (int i = 0; i < statements.size(); i++) {
                Statement stmt = (Statement) statements.get(i);
                Expression exp = null;
                if (stmt instanceof ReturnStatement) {
                    ReturnStatement rs = (ReturnStatement) stmt;
                    exp = rs.getExpression();
                } else if (stmt instanceof ExpressionStatement) {
                    ExpressionStatement es = (ExpressionStatement) stmt;
                    exp = es.getExpression();
                    if (exp instanceof MethodInvocation) {
                        MethodInvocation mi = (MethodInvocation) exp;
                        for (Object arg : mi.arguments()) {
                            if (arg instanceof ClassInstanceCreation) {
                                exp = (Expression) arg;
                                break;
                            }
                        }
                    }
                }
                if (exp instanceof ClassInstanceCreation) {
                    ClassInstanceCreation cic = (ClassInstanceCreation) exp;
                    boolean isRouteBuilder = false;
                    if (cic.getType() instanceof SimpleType) {
                        SimpleType st = (SimpleType) cic.getType();
                        isRouteBuilder = "RouteBuilder".equals(st.getName().toString());
                    }
                    if (isRouteBuilder && cic.getAnonymousClassDeclaration() != null) {
                        List body = cic.getAnonymousClassDeclaration().bodyDeclarations();
                        for (int j = 0; j < body.size(); j++) {
                            Object line = body.get(j);
                            if (line instanceof MethodDeclaration) {
                                MethodDeclaration amd = (MethodDeclaration) line;
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

    public static List<ParserResult> parseCamelConsumerUris(MethodSource<JavaClassSource> method, boolean strings, boolean fields) {
        return doParseCamelUris(method, true, false, strings, fields, false);
    }

    public static List<ParserResult> parseCamelProducerUris(MethodSource<JavaClassSource> method, boolean strings, boolean fields) {
        return doParseCamelUris(method, false, true, strings, fields, false);
    }

    private static List<ParserResult> doParseCamelUris(MethodSource<JavaClassSource> method, boolean consumers, boolean producers,
                                                       boolean strings, boolean fields, boolean routeIdsOnly) {

        List<ParserResult> answer = new ArrayList<ParserResult>();

        if (method != null) {
            MethodDeclaration md = (MethodDeclaration) method.getInternal();
            Block block = md.getBody();
            if (block != null) {
                for (Object statement : md.getBody().statements()) {
                    // must be a method call expression
                    if (statement instanceof ExpressionStatement) {
                        ExpressionStatement es = (ExpressionStatement) statement;
                        Expression exp = es.getExpression();

                        List<ParserResult> uris = new ArrayList<ParserResult>();
                        parseExpression(method.getOrigin(), block, exp, uris, consumers, producers, strings, fields, routeIdsOnly);
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

    private static void parseExpression(JavaClassSource clazz, Block block, Expression exp, List<ParserResult> uris,
                                        boolean consumers, boolean producers, boolean strings, boolean fields, boolean routeIdsOnly) {
        if (exp == null) {
            return;
        }
        if (exp instanceof MethodInvocation) {
            MethodInvocation mi = (MethodInvocation) exp;
            doParseCamelUris(clazz, block, mi, uris, consumers, producers, strings, fields, routeIdsOnly);
            // if the method was called on another method, then recursive
            exp = mi.getExpression();
            parseExpression(clazz, block, exp, uris, consumers, producers, strings, fields, routeIdsOnly);
        }
    }

    private static void doParseCamelUris(JavaClassSource clazz, Block block, MethodInvocation mi, List<ParserResult> uris,
                                         boolean consumers, boolean producers, boolean strings, boolean fields, boolean routeIdsOnly) {
        String name = mi.getName().getIdentifier();

        if (routeIdsOnly) {
            // include route id for consumers
            if ("routeId".equals(name)) {
                List args = mi.arguments();
                if (args != null) {
                    for (Object arg : args) {
                        if (isValidArgument(name, arg)) {
                            String routeId = getLiteralValue(clazz, block, (Expression) arg);
                            if (!Strings.isBlank(routeId)) {
                                int position = ((Expression) arg).getStartPosition();
                                uris.add(new ParserResult(name, position, routeId));
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
                List args = mi.arguments();
                if (args != null) {
                    for (Object arg : args) {
                        if (isValidArgument(name, arg)) {
                            extractEndpointUriFromArgument(name, clazz, block, uris, arg, strings, fields);
                        }
                    }
                }
            }
            if ("fromF".equals(name)) {
                List args = mi.arguments();
                // the first argument is where the uri is
                if (args != null && args.size() >= 1) {
                    Object arg = args.get(0);
                    if (isValidArgument(name, arg)) {
                        extractEndpointUriFromArgument(name, clazz, block, uris, arg, strings, fields);
                    }
                }
            }
            if ("interceptFrom".equals(name)) {
                List args = mi.arguments();
                // the first argument is where the uri is
                if (args != null && args.size() >= 1) {
                    Object arg = args.get(0);
                    if (isValidArgument(name, arg)) {
                        extractEndpointUriFromArgument(name, clazz, block, uris, arg, strings, fields);
                    }
                }
            }
            if ("pollEnrich".equals(name)) {
                List args = mi.arguments();
                // the first argument is where the uri is
                if (args != null && args.size() >= 1) {
                    Object arg = args.get(0);
                    if (isValidArgument(name, arg)) {
                        extractEndpointUriFromArgument(name, clazz, block, uris, arg, strings, fields);
                    }
                }
            }
        }

        if (producers) {
            if ("to".equals(name) || "toD".equals(name)) {
                List args = mi.arguments();
                if (args != null) {
                    for (Object arg : args) {
                        // skip if the arg is a boolean, ExchangePattern or Iterateable, type
                        if (isValidArgument(name, arg)) {
                            extractEndpointUriFromArgument(name, clazz, block, uris, arg, strings, fields);
                        }
                    }
                }
            }
            if ("toF".equals(name)) {
                List args = mi.arguments();
                // the first argument is where the uri is
                if (args != null && args.size() >= 1) {
                    Object arg = args.get(0);
                    if (isValidArgument(name, arg)) {
                        extractEndpointUriFromArgument(name, clazz, block, uris, arg, strings, fields);
                    }
                }
            }
            if ("enrich".equals(name) || "wireTap".equals(name)) {
                List args = mi.arguments();
                // the first argument is where the uri is
                if (args != null && args.size() >= 1) {
                    Object arg = args.get(0);
                    if (isValidArgument(name, arg)) {
                        extractEndpointUriFromArgument(name, clazz, block, uris, arg, strings, fields);
                    }
                }
            }
        }
    }

    private static boolean isValidArgument(String node, Object arg) {
        // skip boolean argument, as toD can accept a boolean value
        if (arg instanceof BooleanLiteral) {
            return false;
        }
        // skip ExchangePattern argument
        if (arg instanceof QualifiedName) {
            QualifiedName qn = (QualifiedName) arg;
            String name = qn.getFullyQualifiedName();
            if (name.startsWith("ExchangePattern")) {
                return false;
            }
        }
        return true;
    }

    private static void extractEndpointUriFromArgument(String node, JavaClassSource clazz, Block block, List<ParserResult> uris, Object arg, boolean strings, boolean fields) {
        if (strings) {
            String uri = getLiteralValue(clazz, block, (Expression) arg);
            if (!Strings.isBlank(uri)) {
                int position = ((Expression) arg).getStartPosition();

                // if the node is fromF or toF, then replace all %X with {{%X}} as we cannot parse that value
                if ("fromF".equals(node) || "toF".equals(node)) {
                    uri = uri.replaceAll("\\%s", "\\{\\{\\%s\\}\\}");
                    uri = uri.replaceAll("\\%d", "\\{\\{\\%d\\}\\}");
                    uri = uri.replaceAll("\\%b", "\\{\\{\\%b\\}\\}");
                }

                uris.add(new ParserResult(node, position, uri));
                return;
            }
        }
        if (fields && arg instanceof SimpleName) {
            FieldSource field = getField(clazz, block, (SimpleName) arg);
            if (field != null) {
                // find the endpoint uri from the annotation
                AnnotationSource annotation = field.getAnnotation("org.apache.camel.cdi.Uri");
                if (annotation == null) {
                    annotation = field.getAnnotation("org.apache.camel.EndpointInject");
                }
                if (annotation != null) {
                    Expression exp = (Expression) annotation.getInternal();
                    if (exp instanceof SingleMemberAnnotation) {
                        exp = ((SingleMemberAnnotation) exp).getValue();
                    } else if (exp instanceof NormalAnnotation) {
                        List values = ((NormalAnnotation) exp).values();
                        for (Object value : values) {
                            MemberValuePair pair = (MemberValuePair) value;
                            if ("uri".equals(pair.getName().toString())) {
                                exp = pair.getValue();
                                break;
                            }
                        }
                    }
                    String uri = CamelJavaParserHelper.getLiteralValue(clazz, block, exp);
                    if (!Strings.isBlank(uri)) {
                        int position = ((SimpleName) arg).getStartPosition();
                        uris.add(new ParserResult(node, position, uri));
                    }
                } else {
                    // the field may be initialized using variables, so we need to evaluate those expressions
                    Object fi = field.getInternal();
                    if (fi instanceof VariableDeclaration) {
                        Expression exp = ((VariableDeclaration) fi).getInitializer();
                        String uri = CamelJavaParserHelper.getLiteralValue(clazz, block, exp);
                        if (!Strings.isBlank(uri)) {
                            // we want the position of the field, and not in the route
                            int position = ((VariableDeclaration) fi).getStartPosition();
                            uris.add(new ParserResult(node, position, uri));
                        }
                    }
                }
            }
        }

        // cannot parse it so add a failure
        uris.add(new ParserResult(node, -1, arg.toString(), false));
    }

    public static List<ParserResult> parseCamelSimpleExpressions(MethodSource<JavaClassSource> method) {
        List<ParserResult> answer = new ArrayList<ParserResult>();

        MethodDeclaration md = (MethodDeclaration) method.getInternal();
        Block block = md.getBody();
        if (block != null) {
            for (Object statement : block.statements()) {
                // must be a method call expression
                if (statement instanceof ExpressionStatement) {
                    ExpressionStatement es = (ExpressionStatement) statement;
                    Expression exp = es.getExpression();

                    List<ParserResult> expressions = new ArrayList<ParserResult>();
                    parseExpression(null, method.getOrigin(), block, exp, expressions);
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

    private static void parseExpression(String node, JavaClassSource clazz, Block block, Expression exp, List<ParserResult> expressions) {
        if (exp == null) {
            return;
        }
        if (exp instanceof MethodInvocation) {
            MethodInvocation mi = (MethodInvocation) exp;
            doParseCamelSimple(node, clazz, block, mi, expressions);
            // if the method was called on another method, then recursive
            exp = mi.getExpression();
            parseExpression(node, clazz, block, exp, expressions);
        }
    }

    private static void doParseCamelSimple(String node, JavaClassSource clazz, Block block, MethodInvocation mi, List<ParserResult> expressions) {
        String name = mi.getName().getIdentifier();

        if ("simple".equals(name)) {
            List args = mi.arguments();
            // the first argument is a string parameter for the simple expression
            if (args != null && args.size() >= 1) {
                // it is a String type
                Object arg = args.get(0);
                String simple = getLiteralValue(clazz, block, (Expression) arg);
                if (!Strings.isBlank(simple)) {
                    // is this a simple expression that is called as a predicate or expression
                    boolean predicate = false;
                    Expression parent = mi.getExpression();
                    if (parent == null) {
                        // maybe its an argument
                        // simple maybe be passed in as an argument
                        List list = mi.arguments();
                        // must be a single argument
                        if (list != null && list.size() == 1) {
                            ASTNode o = (ASTNode) list.get(0);
                            ASTNode p = o.getParent();
                            if (p instanceof MethodInvocation) {
                                // this is simple
                                String pName = ((MethodInvocation) p).getName().getIdentifier();
                                if ("simple".equals(pName)) {
                                    // okay find the parent of simple which is the method that uses simple
                                    parent = (Expression) p.getParent();
                                }
                            }
                        }
                    }
                    if (parent instanceof MethodInvocation) {
                        MethodInvocation emi = (MethodInvocation) parent;
                        String parentName = emi.getName().getIdentifier();
                        predicate = isSimplePredicate(parentName);
                    }

                    int position = ((Expression) arg).getStartPosition();
                    ParserResult result = new ParserResult(node, position, simple);
                    result.setPredicate(predicate);
                    expressions.add(result);
                }
            }
        }

        // simple maybe be passed in as an argument
        List args = mi.arguments();
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof MethodInvocation) {
                    MethodInvocation ami = (MethodInvocation) arg;
                    doParseCamelSimple(node, clazz, block, ami, expressions);
                }
            }
        }
    }

    /**
     * Using simple expressions in the Java DSL may be used in certain places as predicate only
     */
    private static boolean isSimplePredicate(String name) {
        if (name == null) {
            return false;
        }
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

    @SuppressWarnings("unchecked")
    private static FieldSource<JavaClassSource> getField(JavaClassSource clazz, Block block, SimpleName ref) {
        String fieldName = ref.getIdentifier();
        if (fieldName != null) {
            // find field in class
            FieldSource field = clazz != null ? clazz.getField(fieldName) : null;
            if (field == null) {
                field = findFieldInBlock(clazz, block, fieldName);
            }
            return field;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static FieldSource<JavaClassSource> findFieldInBlock(JavaClassSource clazz, Block block, String fieldName) {
        for (Object statement : block.statements()) {
            // try local statements first in the block
            if (statement instanceof VariableDeclarationStatement) {
                final Type type = ((VariableDeclarationStatement) statement).getType();
                for (Object obj : ((VariableDeclarationStatement) statement).fragments()) {
                    if (obj instanceof VariableDeclarationFragment) {
                        VariableDeclarationFragment fragment = (VariableDeclarationFragment) obj;
                        SimpleName name = fragment.getName();
                        if (name != null && fieldName.equals(name.getIdentifier())) {
                            return new StatementFieldSource(clazz, fragment, type);
                        }
                    }
                }
            }

            // okay the field may be burried inside an anonymous inner class as a field declaration
            // outside the configure method, so lets go back to the parent and see what we can find
            ASTNode node = block.getParent();
            if (node instanceof MethodDeclaration) {
                node = node.getParent();
            }
            if (node instanceof AnonymousClassDeclaration) {
                List declarations = ((AnonymousClassDeclaration) node).bodyDeclarations();
                for (Object dec : declarations) {
                    if (dec instanceof FieldDeclaration) {
                        FieldDeclaration fd = (FieldDeclaration) dec;
                        final Type type = fd.getType();
                        for (Object obj : fd.fragments()) {
                            if (obj instanceof VariableDeclarationFragment) {
                                VariableDeclarationFragment fragment = (VariableDeclarationFragment) obj;
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

    public static String getLiteralValue(JavaClassSource clazz, Block block, Expression expression) {
        // unwrap parenthesis
        if (expression instanceof ParenthesizedExpression) {
            expression = ((ParenthesizedExpression) expression).getExpression();
        }

        if (expression instanceof StringLiteral) {
            return ((StringLiteral) expression).getLiteralValue();
        } else if (expression instanceof BooleanLiteral) {
            return "" + ((BooleanLiteral) expression).booleanValue();
        } else if (expression instanceof NumberLiteral) {
            return ((NumberLiteral) expression).getToken();
        }

        // if it a method invocation then add a dummy value assuming the method invocation will return a valid response
        if (expression instanceof MethodInvocation) {
            String name = ((MethodInvocation) expression).getName().getIdentifier();
            return "{{" + name + "}}";
        }

        // if its a qualified name (usually a constant field in another class)
        // then add a dummy value as we cannot find the field value in other classes and maybe even outside the
        // source code we have access to
        if (expression instanceof QualifiedName) {
            QualifiedName qn = (QualifiedName) expression;
            String name = qn.getFullyQualifiedName();
            return "{{" + name + "}}";
        }

        if (expression instanceof SimpleName) {
            FieldSource<JavaClassSource> field = getField(clazz, block, (SimpleName) expression);
            if (field != null) {
                // is the field annotated with a Camel endpoint
                if (field.getAnnotations() != null) {
                    for (Annotation ann : field.getAnnotations()) {
                        boolean valid = "org.apache.camel.EndpointInject".equals(ann.getQualifiedName()) || "org.apache.camel.cdi.Uri".equals(ann.getQualifiedName());
                        if (valid) {
                            Expression exp = (Expression) ann.getInternal();
                            if (exp instanceof SingleMemberAnnotation) {
                                exp = ((SingleMemberAnnotation) exp).getValue();
                            } else if (exp instanceof NormalAnnotation) {
                                List values = ((NormalAnnotation) exp).values();
                                for (Object value : values) {
                                    MemberValuePair pair = (MemberValuePair) value;
                                    if ("uri".equals(pair.getName().toString())) {
                                        exp = pair.getValue();
                                        break;
                                    }
                                }
                            }
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
                    if (expression instanceof MethodInvocation) {
                        MethodInvocation mi = (MethodInvocation) expression;
                        List args = mi.arguments();
                        if (args != null && args.size() > 0) {
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
                        // its a field which has no initializer, then add a dummy value assuming the field will be initialized at runtime
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
        } else if (expression instanceof InfixExpression) {
            String answer = null;
            // is it a string that is concat together?
            InfixExpression ie = (InfixExpression) expression;
            if (InfixExpression.Operator.PLUS.equals(ie.getOperator())) {

                String val1 = getLiteralValue(clazz, block, ie.getLeftOperand());
                String val2 = getLiteralValue(clazz, block, ie.getRightOperand());

                // if numeric then we plus the values, otherwise we string concat
                boolean numeric = isNumericOperator(clazz, block, ie.getLeftOperand()) && isNumericOperator(clazz, block, ie.getRightOperand());
                if (numeric) {
                    Long num1 = val1 != null ? Long.valueOf(val1) : 0;
                    Long num2 = val2 != null ? Long.valueOf(val2) : 0;
                    answer = "" + (num1 + num2);
                } else {
                    answer = (val1 != null ? val1 : "") + (val2 != null ? val2 : "");
                }

                if (!answer.isEmpty()) {
                    // include extended when we concat on 2 or more lines
                    List extended = ie.extendedOperands();
                    if (extended != null) {
                        for (Object ext : extended) {
                            String val3 = getLiteralValue(clazz, block, (Expression) ext);
                            if (numeric) {
                                Long num3 = val3 != null ? Long.valueOf(val3) : 0;
                                Long num = Long.valueOf(answer);
                                answer = "" + (num + num3);
                            } else {
                                answer += val3 != null ? val3 : "";
                            }
                        }
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
            FieldSource field = getField(clazz, block, (SimpleName) expression);
            if (field != null) {
                return field.getType().isType("int") || field.getType().isType("long")
                        || field.getType().isType("Integer") || field.getType().isType("Long");
            }
        }
        return false;
    }

}
