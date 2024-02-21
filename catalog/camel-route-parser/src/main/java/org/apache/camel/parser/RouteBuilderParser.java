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
package org.apache.camel.parser;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.camel.parser.helper.CamelJavaParserHelper;
import org.apache.camel.parser.helper.CamelJavaTreeParserHelper;
import org.apache.camel.parser.model.CamelCSimpleExpressionDetails;
import org.apache.camel.parser.model.CamelEndpointDetails;
import org.apache.camel.parser.model.CamelNodeDetails;
import org.apache.camel.parser.model.CamelRouteDetails;
import org.apache.camel.parser.model.CamelSimpleExpressionDetails;
import org.apache.camel.tooling.util.Strings;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ASTNode;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Expression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MemberValuePair;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.NormalAnnotation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.jboss.forge.roaster.model.Annotation;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;

/**
 * A Camel RouteBuilder parser that parses Camel Java routes source code.
 * <p/>
 * This implementation is higher level details, and uses the lower level parser {@link CamelJavaParserHelper}.
 */
public final class RouteBuilderParser {

    public static final String METHOD_NAME = "configure";

    private RouteBuilderParser() {
    }

    /**
     * Parses the java source class and build a route model (tree) of the discovered routes in the java source class.
     *
     * @param  clazz                  the java source class
     * @param  fullyQualifiedFileName the fully qualified source code file name
     * @return                        a list of route model (tree) of each discovered route
     */
    public static List<CamelNodeDetails> parseRouteBuilderTree(
            JavaClassSource clazz, String fullyQualifiedFileName,
            boolean includeInlinedRouteBuilders) {

        List<MethodSource<JavaClassSource>> methods = findAllConfigureMethods(clazz, includeInlinedRouteBuilders);

        CamelJavaTreeParserHelper parser = new CamelJavaTreeParserHelper();
        List<CamelNodeDetails> list = new ArrayList<>();
        for (MethodSource<JavaClassSource> configureMethod : methods) {
            // there may be multiple route builder configure methods
            List<CamelNodeDetails> details
                    = parser.parseCamelRouteTree(clazz, fullyQualifiedFileName, configureMethod);
            list.addAll(details);
        }
        // we end up parsing bottom->up so reverse list
        Collections.reverse(list);

        return list;
    }

    /**
     * Parses the java source class to discover Camel endpoints.
     *
     * @param clazz                  the java source class
     * @param baseDir                the base of the source code
     * @param fullyQualifiedFileName the fully qualified source code file name
     * @param endpoints              list to add discovered and parsed endpoints
     */
    public static void parseRouteBuilderEndpoints(
            JavaClassSource clazz, String baseDir, String fullyQualifiedFileName,
            List<CamelEndpointDetails> endpoints) {
        parseRouteBuilderEndpoints(clazz, baseDir, fullyQualifiedFileName, endpoints, null, false);
    }

    /**
     * Parses the java source class to discover Camel endpoints.
     *
     * @param clazz                       the java source class
     * @param baseDir                     the base of the source code
     * @param fullyQualifiedFileName      the fully qualified source code file name
     * @param endpoints                   list to add discovered and parsed endpoints
     * @param unparsable                  list of unparsable nodes
     * @param includeInlinedRouteBuilders whether to include inlined route builders in the parsing
     */
    public static void parseRouteBuilderEndpoints(
            JavaClassSource clazz, String baseDir, String fullyQualifiedFileName,
            List<CamelEndpointDetails> endpoints, List<String> unparsable, boolean includeInlinedRouteBuilders) {

        // look for fields which are not used in the route
        for (FieldSource<JavaClassSource> field : clazz.getFields()) {

            // is the field annotated with a Camel endpoint
            String uri = null;
            Expression exp = null;
            for (Annotation<JavaClassSource> ann : field.getAnnotations()) {
                boolean valid = "org.apache.camel.EndpointInject".equals(ann.getQualifiedName())
                        || "org.apache.camel.cdi.Uri".equals(ann.getQualifiedName());
                if (valid) {
                    exp = (Expression) ann.getInternal();
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
                    uri = CamelJavaParserHelper.getLiteralValue(clazz, null, exp);
                }
            }

            // we only want to add fields which are not used in the route
            if (!Strings.isNullOrEmpty(uri) && findEndpointByUri(endpoints, uri) == null) {

                // we only want the relative dir name from the
                String fileName = parseFileName(baseDir, fullyQualifiedFileName);
                String id = field.getName();

                CamelEndpointDetails detail = new CamelEndpointDetails();
                detail.setFileName(fileName);
                detail.setClassName(clazz.getQualifiedName());
                detail.setEndpointInstance(id);
                detail.setEndpointUri(uri);
                detail.setEndpointComponentName(endpointComponentName(uri));

                // favor the position of the expression which had the actual uri
                Object internal = exp != null ? exp : field.getInternal();

                // find position of field/expression
                if (internal instanceof ASTNode astNode) {
                    int pos = astNode.getStartPosition();
                    int len = astNode.getLength();
                    int line = findLineNumber(clazz.toUnformattedString(), pos);
                    if (line > -1) {
                        detail.setLineNumber(Integer.toString(line));
                    }
                    int endLine = findLineNumber(clazz.toUnformattedString(), pos + len);
                    if (endLine > -1) {
                        detail.setLineNumberEnd(Integer.toString(endLine));
                    }
                    detail.setAbsolutePosition(pos);
                    int linePos = findLinePosition(clazz.toUnformattedString(), pos);
                    if (linePos > -1) {
                        detail.setLinePosition(linePos);
                    }
                }
                // we do not know if this field is used as consumer or producer only, but we try
                // to find out by scanning the route in the configure method below
                endpoints.add(detail);
            }
        }

        // find all the configure methods
        List<MethodSource<JavaClassSource>> methods = findAllConfigureMethods(clazz, includeInlinedRouteBuilders);

        // look if any of these fields are used in the route only as consumer or producer, as then we can
        // determine this to ensure when we edit the endpoint we should only the options accordingly
        for (MethodSource<JavaClassSource> configureMethod : methods) {
            // consumers only
            List<ParserResult> uris = CamelJavaParserHelper.parseCamelConsumerUris(configureMethod, true, true);
            for (ParserResult result : uris) {
                if (!result.isParsed()) {
                    if (unparsable != null) {
                        unparsable.add(result.getElement());
                    }
                } else {
                    String fileName = parseFileName(baseDir, fullyQualifiedFileName);

                    CamelEndpointDetails detail = buildCamelEndpointDetails(clazz, configureMethod, result, fileName);
                    detail.setConsumerOnly(true);
                    detail.setProducerOnly(false);
                    endpoints.add(detail);
                }
            }
            // producer only
            uris = CamelJavaParserHelper.parseCamelProducerUris(configureMethod, true, true);
            for (ParserResult result : uris) {
                if (!result.isParsed()) {
                    if (unparsable != null) {
                        unparsable.add(result.getElement());
                    }
                } else {
                    // the same endpoint uri may be used in multiple places in the same route
                    // so we should maybe add all of them
                    String fileName = parseFileName(baseDir, fullyQualifiedFileName);

                    CamelEndpointDetails detail = buildCamelEndpointDetails(clazz, configureMethod, result, fileName);
                    detail.setConsumerOnly(false);
                    detail.setProducerOnly(true);
                    endpoints.add(detail);
                }
            }
        }
    }

    static List<MethodSource<JavaClassSource>> findAllConfigureMethods(
            JavaClassSource clazz, boolean includeInlinedRouteBuilders) {
        List<MethodSource<JavaClassSource>> methods = new ArrayList<>();
        MethodSource<JavaClassSource> method = CamelJavaParserHelper.findConfigureMethod(clazz);
        if (method != null) {
            methods.add(method);
        }
        if (includeInlinedRouteBuilders) {
            List<MethodSource<JavaClassSource>> inlinedMethods = CamelJavaParserHelper.findInlinedConfigureMethods(clazz);
            if (!inlinedMethods.isEmpty()) {
                methods.addAll(inlinedMethods);
            }
        }
        return methods;
    }

    private static CamelEndpointDetails buildCamelEndpointDetails(
            JavaClassSource clazz, MethodSource<JavaClassSource> configureMethod, ParserResult result, String fileName) {
        CamelEndpointDetails detail = new CamelEndpointDetails();
        detail.setFileName(fileName);
        detail.setClassName(clazz.getQualifiedName());
        detail.setMethodName(configureMethod.getName());
        detail.setEndpointInstance(null);
        detail.setEndpointUri(result.getElement());
        int line = findLineNumber(clazz.toUnformattedString(), result.getPosition());
        if (line > -1) {
            detail.setLineNumber(Integer.toString(line));
        }
        int lineEnd = findLineNumber(clazz.toUnformattedString(), result.getPosition() + result.getLength());
        if (lineEnd > -1) {
            detail.setLineNumberEnd(Integer.toString(lineEnd));
        }
        detail.setAbsolutePosition(result.getPosition());
        int linePos = findLinePosition(clazz.toUnformattedString(), result.getPosition());
        if (linePos > -1) {
            detail.setLinePosition(linePos);
        }
        detail.setEndpointComponentName(endpointComponentName(result.getElement()));
        return detail;
    }

    private static String parseFileName(String baseDir, String fullyQualifiedFileName) {
        String fileName = fullyQualifiedFileName;
        if (fileName.startsWith(baseDir)) {
            fileName = fileName.substring(baseDir.length() + 1);
        }
        return fileName;
    }

    /**
     * Parses the java source class to discover Camel simple expressions.
     *
     * @param clazz                  the java source class
     * @param baseDir                the base of the source code
     * @param fullyQualifiedFileName the fully qualified source code file name
     * @param simpleExpressions      list to add discovered and parsed simple expressions
     */
    public static void parseRouteBuilderSimpleExpressions(
            JavaClassSource clazz, String baseDir, String fullyQualifiedFileName,
            List<CamelSimpleExpressionDetails> simpleExpressions) {
        MethodSource<JavaClassSource> method = CamelJavaParserHelper.findConfigureMethod(clazz);
        if (method != null) {
            List<ParserResult> expressions = CamelJavaParserHelper.parseCamelLanguageExpressions(method, "simple");
            for (ParserResult result : expressions) {
                if (result.isParsed()) {
                    String fileName = parseFileName(baseDir, fullyQualifiedFileName);

                    CamelSimpleExpressionDetails detail = new CamelSimpleExpressionDetails();
                    detail.setFileName(fileName);
                    detail.setClassName(clazz.getQualifiedName());
                    detail.setMethodName(METHOD_NAME);
                    int line = findLineNumber(clazz.toUnformattedString(), result.getPosition());
                    if (line > -1) {
                        detail.setLineNumber(Integer.toString(line));
                    }
                    int endLine = findLineNumber(clazz.toUnformattedString(), result.getPosition() + result.getLength());
                    if (endLine > -1) {
                        detail.setLineNumberEnd(Integer.toString(endLine));
                    }
                    detail.setAbsolutePosition(result.getPosition());
                    int linePos = findLinePosition(clazz.toUnformattedString(), result.getPosition());
                    if (linePos > -1) {
                        detail.setLinePosition(linePos);
                    }
                    detail.setSimple(result.getElement());

                    boolean predicate = result.getPredicate() != null ? result.getPredicate() : false;
                    boolean expression = !predicate;
                    detail.setPredicate(predicate);
                    detail.setExpression(expression);

                    simpleExpressions.add(detail);
                }
            }
        }
    }

    /**
     * Parses the java source class to discover Camel compiled simple expressions.
     *
     * @param clazz                  the java source class
     * @param baseDir                the base of the source code
     * @param fullyQualifiedFileName the fully qualified source code file name
     * @param csimpleExpressions     list to add discovered and parsed simple expressions
     */
    public static void parseRouteBuilderCSimpleExpressions(
            JavaClassSource clazz, String baseDir, String fullyQualifiedFileName,
            List<CamelCSimpleExpressionDetails> csimpleExpressions) {
        MethodSource<JavaClassSource> method = CamelJavaParserHelper.findConfigureMethod(clazz);
        if (method != null) {
            List<ParserResult> expressions = CamelJavaParserHelper.parseCamelLanguageExpressions(method, "csimple");
            for (ParserResult result : expressions) {
                if (result.isParsed()) {
                    checkParsedResult(clazz, baseDir, fullyQualifiedFileName, csimpleExpressions, result);
                }
            }
        }
    }

    private static void checkParsedResult(
            JavaClassSource clazz, String baseDir, String fullyQualifiedFileName,
            List<CamelCSimpleExpressionDetails> csimpleExpressions, ParserResult result) {
        String fileName = parseFileName(baseDir, fullyQualifiedFileName);

        CamelCSimpleExpressionDetails detail = new CamelCSimpleExpressionDetails();
        detail.setFileName(fileName);
        detail.setClassName(clazz.getQualifiedName());
        detail.setMethodName(METHOD_NAME);
        int line = findLineNumber(clazz.toUnformattedString(), result.getPosition());
        if (line > -1) {
            detail.setLineNumber(Integer.toString(line));
        }
        int endLine = findLineNumber(clazz.toUnformattedString(), result.getPosition() + result.getLength());
        if (endLine > -1) {
            detail.setLineNumberEnd(Integer.toString(endLine));
        }
        detail.setAbsolutePosition(result.getPosition());
        int linePos = findLinePosition(clazz.toUnformattedString(), result.getPosition());
        if (linePos > -1) {
            detail.setLinePosition(linePos);
        }
        detail.setCsimple(result.getElement());

        boolean predicate = result.getPredicate() != null ? result.getPredicate() : false;
        boolean expression = !predicate;
        detail.setPredicate(predicate);
        detail.setExpression(expression);

        csimpleExpressions.add(detail);
    }

    /**
     * Parses the java source class to discover Camel routes with id's assigned.
     *
     * @param clazz                  the java source class
     * @param baseDir                the base of the source code
     * @param fullyQualifiedFileName the fully qualified source code file name
     * @param routes                 list to add discovered and parsed routes
     */
    public static void parseRouteBuilderRouteIds(
            JavaClassSource clazz, String baseDir, String fullyQualifiedFileName,
            List<CamelRouteDetails> routes) {

        MethodSource<JavaClassSource> method = CamelJavaParserHelper.findConfigureMethod(clazz);
        if (method != null) {
            List<ParserResult> expressions = CamelJavaParserHelper.parseCamelRouteIds(method);
            for (ParserResult result : expressions) {
                // route ids is assigned in java dsl using the routeId method
                if (result.isParsed()) {
                    String fileName = parseFileName(baseDir, fullyQualifiedFileName);

                    CamelRouteDetails detail = new CamelRouteDetails();
                    detail.setFileName(fileName);
                    detail.setClassName(clazz.getQualifiedName());
                    detail.setMethodName(METHOD_NAME);
                    int line = findLineNumber(clazz.toUnformattedString(), result.getPosition());
                    if (line > -1) {
                        detail.setLineNumber(Integer.toString(line));
                    }
                    int endLine = findLineNumber(clazz.toUnformattedString(), result.getPosition() + result.getLength());
                    if (endLine > -1) {
                        detail.setLineNumberEnd(Integer.toString(endLine));
                    }
                    detail.setRouteId(result.getElement());

                    routes.add(detail);
                }
            }
        }
    }

    private static CamelEndpointDetails findEndpointByUri(List<CamelEndpointDetails> endpoints, String uri) {
        for (CamelEndpointDetails detail : endpoints) {
            if (uri.equals(detail.getEndpointUri())) {
                return detail;
            }
        }
        return null;
    }

    private static int findLineNumber(String content, int position) {
        int lines = 0;

        try {
            int current = 0;
            try (BufferedReader br = new BufferedReader(new StringReader(content))) {
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

    private static int findLinePosition(String content, int position) {
        int lines = 0;

        try {
            int current = 0;
            try (BufferedReader br = new BufferedReader(new StringReader(content))) {
                String line;
                while ((line = br.readLine()) != null) {
                    lines++;
                    current += line.length() + 1; // add 1 for line feed
                    if (current >= position) {
                        int prev = current - (line.length() + 1);
                        // find relative position now
                        int rel = position - prev;
                        // add +1
                        return rel + 1;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
            return -1;
        }

        return lines;
    }

    private static String endpointComponentName(String uri) {
        if (uri != null) {
            int idx = uri.indexOf(':');
            if (idx > 0) {
                return uri.substring(0, idx);
            }
        }
        return null;
    }

}
