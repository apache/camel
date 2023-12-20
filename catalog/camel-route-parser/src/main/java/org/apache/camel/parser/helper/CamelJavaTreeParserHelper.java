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
import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.parser.model.CamelNodeDetails;
import org.apache.camel.parser.model.CamelNodeDetailsFactory;
import org.apache.camel.tooling.model.JsonMapper;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Block;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.BooleanLiteral;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Expression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ExpressionStatement;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.InfixExpression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MemberValuePair;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodInvocation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.NormalAnnotation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.NumberLiteral;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.QualifiedName;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.SimpleName;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.StringLiteral;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.TextBlock;
import org.jboss.forge.roaster.model.Annotation;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;

import static org.apache.camel.parser.helper.CamelJavaParserHelper.endpointTypeCheck;
import static org.apache.camel.parser.helper.ParserCommon.findLineNumber;

/**
 * A Camel Java tree parser that only depends on the Roaster API.
 * <p/>
 * This implement is used for parsing the Camel routes and build a tree structure of the EIP nodes.
 *
 * @see CamelJavaParserHelper for parser that can discover endpoints and simple expressions
 */
public final class CamelJavaTreeParserHelper {

    private final CamelCatalog camelCatalog = new DefaultCamelCatalog(true);

    public List<CamelNodeDetails> parseCamelRouteTree(
            JavaClassSource clazz, String fullyQualifiedFileName,
            MethodSource<JavaClassSource> configureMethod) {

        // find any from which is the start of the route
        CamelNodeDetailsFactory nodeFactory = CamelNodeDetailsFactory.newInstance();

        CamelNodeDetails route = nodeFactory.newNode(null, "route");

        if (configureMethod != null) {
            MethodDeclaration md = (MethodDeclaration) configureMethod.getInternal();
            Block block = md.getBody();
            if (block != null) {
                for (Object statement : md.getBody().statements()) {
                    // must be a method call expression
                    if (statement instanceof ExpressionStatement es) {
                        Expression exp = es.getExpression();
                        boolean valid = isFromCamelRoute(exp);
                        if (valid) {
                            parseExpression(nodeFactory, fullyQualifiedFileName, clazz, configureMethod, block, exp, route);
                        }
                    }
                }
            }
        }

        List<CamelNodeDetails> answer = new ArrayList<>();

        if (route.getOutputs() == null || route.getOutputs().isEmpty()) {
            // okay no routes found
            return answer;
        }

        // now parse the route node and build the correct model/tree structure of the EIPs

        // re-create factory as we rebuild the tree
        nodeFactory = CamelNodeDetailsFactory.newInstance();
        CamelNodeDetails parent = route.getOutputs().get(0);

        for (int i = 0; i < route.getOutputs().size(); i++) {
            CamelNodeDetails node = route.getOutputs().get(i);
            String name = node.getName();

            if ("from".equals(name)) {
                CamelNodeDetails from = nodeFactory.copyNode(null, "from", node);
                from.setFileName(fullyQualifiedFileName);
                answer.add(from);
                parent = from;
            } else if ("routeId".equals(name)) {
                // should be set on the parent
                parent.setRouteId(node.getRouteId());
            } else if ("end".equals(name) || "endParent".equals(name) || "endRest".equals(name)
                    || "endDoTry".equals(name)) {
                // parent should be grand parent
                if (parent.getParent() != null) {
                    parent = parent.getParent();
                }
            } else if ("endChoice".equals(name)) {
                // we are in a choice block so parent should be the first choice up the parent tree
                while (!"from".equals(parent.getName()) && !"choice".equals(parent.getName())) {
                    if (parent.getParent() != null) {
                        parent = parent.getParent();
                    } else {
                        break;
                    }
                }
            } else if ("choice".equals(name)) {
                // special for some EIPs
                CamelNodeDetails output = nodeFactory.copyNode(parent, name, node);
                parent.addOutput(output);
                parent = output;
            } else if ("when".equals(name) || "otherwise".equals(name)) {
                // we are in a choice block so parent should be the first choice up the parent tree
                while (!"from".equals(parent.getName()) && !"choice".equals(parent.getName())) {
                    if (parent.getParent() != null) {
                        parent = parent.getParent();
                    } else {
                        break;
                    }
                }
            } else {
                boolean hasOutput = hasOutput(name);
                if (hasOutput) {
                    // has output so add as new child node
                    CamelNodeDetails output = nodeFactory.copyNode(parent, name, node);
                    parent.addOutput(output);
                    parent = output;
                } else {
                    // add straight to itself
                    CamelNodeDetails output = nodeFactory.copyNode(parent, name, node);
                    parent.addOutput(output);
                }
            }
        }

        return answer;
    }

    private boolean isFromCamelRoute(Expression exp) {
        String rootMethodName = null;

        // find out if this is from a Camel route (eg from, route etc.)
        Expression sub = exp;
        while (sub instanceof MethodInvocation) {
            sub = ((MethodInvocation) sub).getExpression();
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
        } else if (sub == null && exp instanceof MethodInvocation methodInvocation) {
            rootMethodName = methodInvocation.getName().getIdentifier();
        }
        // a route starts either via from or route
        return "from".equals(rootMethodName) || "route".equals(rootMethodName);
    }

    private boolean hasOutput(String name) {
        String json = camelCatalog.modelJSonSchema(name);
        return JsonMapper.generateEipModel(json).isOutput();
    }

    private boolean hasInput(String name) {
        String json = camelCatalog.modelJSonSchema(name);
        return JsonMapper.generateEipModel(json).isInput();
    }

    private static CamelNodeDetails grandParent(CamelNodeDetails node, String parentName) {
        if (node == null) {
            return null;
        }
        if (parentName.equals(node.getName())) {
            return node;
        } else {
            return grandParent(node.getParent(), parentName);
        }
    }

    private void parseExpression(
            CamelNodeDetailsFactory nodeFactory, String fullyQualifiedFileName,
            JavaClassSource clazz, MethodSource<JavaClassSource> configureMethod, Block block,
            Expression exp, CamelNodeDetails node) {
        if (exp == null) {
            return;
        }
        if (exp instanceof MethodInvocation mi) {
            node = doParseCamelModels(nodeFactory, fullyQualifiedFileName, clazz, configureMethod, block, mi, node);
            // if the method was called on another method, then recursive
            exp = mi.getExpression();
            parseExpression(nodeFactory, fullyQualifiedFileName, clazz, configureMethod, block, exp, node);
        }
    }

    private CamelNodeDetails doParseCamelModels(
            CamelNodeDetailsFactory nodeFactory, String fullyQualifiedFileName,
            JavaClassSource clazz, MethodSource<JavaClassSource> configureMethod, Block block,
            MethodInvocation mi, CamelNodeDetails node) {
        String name = mi.getName().getIdentifier();

        // special for Java DSL having some endXXX
        boolean isEnd = "end".equals(name) || "endChoice".equals(name) || "endDoTry".equals(name)
                || "endParent".equals(name) || "endRest".equals(name);
        boolean isRoute = "route".equals(name) || "from".equals(name) || "routeId".equals(name);
        // must be an eip model that has either input or output as we only want to track processors (also accept from)
        boolean isEip = camelCatalog.findModelNames().contains(name) && (hasInput(name) || hasOutput(name));

        // only include if its a known Camel model (dont include languages)
        if (isEnd || isRoute || isEip) {
            CamelNodeDetails newNode = nodeFactory.newNode(node, name);

            // include source code details
            int pos = mi.getName().getStartPosition();
            int line = findLineNumber(fullyQualifiedFileName, pos);
            if (line > -1) {
                newNode.setLineNumber(Integer.toString(line));
            }
            pos = mi.getName().getStartPosition() + mi.getName().getLength();
            line = findLineNumber(fullyQualifiedFileName, pos);
            if (line > -1) {
                newNode.setLineNumberEnd(Integer.toString(line));
            }
            newNode.setFileName(fullyQualifiedFileName);
            newNode.setClassName(clazz.getQualifiedName());
            newNode.setMethodName(configureMethod.getName());

            if ("routeId".equals(name)) {
                // grab the route id
                List<?> args = mi.arguments();
                if (args != null && !args.isEmpty()) {
                    // the first argument has the route id
                    Expression exp = (Expression) args.get(0);
                    String routeId = getLiteralValue(clazz, block, exp);
                    if (routeId != null) {
                        newNode.setRouteId(routeId);
                    }
                }
            }

            node.addPreliminaryOutput(newNode);
            return node;
        }

        return node;
    }

    /**
     * @deprecated currently not in use
     */
    @Deprecated
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
        if (expression instanceof MethodInvocation) {
            String name = ((MethodInvocation) expression).getName().getIdentifier();
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
                            Expression exp = (Expression) ann.getInternal();
                            if (exp instanceof SingleMemberAnnotation) {
                                exp = ((SingleMemberAnnotation) exp).getValue();
                            } else if (exp instanceof NormalAnnotation) {
                                List<?> values = ((NormalAnnotation) exp).values();
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
}
