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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.camel.parser.helper.CamelJavaParserHelper;
import org.apache.camel.parser.helper.CamelJavaRestDslParserHelper;
import org.apache.camel.parser.model.RestConfigurationDetails;
import org.apache.camel.parser.model.RestServiceDetails;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;

/**
 * A Camel parser that parses Camel Java Rest DSL source code.
 * <p/>
 * This implementation is higher level details, and uses the lower level parser {@link CamelJavaRestDslParserHelper}.
 */
public final class RestDslParser {

    private RestDslParser() {
    }

    /**
     * Parses the java source class and build a rest configuration model of the discovered rest configurations in the java source class.
     *
     * @param clazz                   the java source class
     * @param baseDir                 the base of the source code
     * @param fullyQualifiedFileName  the fully qualified source code file name
     * @return a list of rest configurations (often there is only one)
     */
    public static List<RestConfigurationDetails> parseRestConfiguration(JavaClassSource clazz, String baseDir, String fullyQualifiedFileName,
                                                                        boolean includeInlinedRouteBuilders) {

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

        CamelJavaRestDslParserHelper parser = new CamelJavaRestDslParserHelper();
        List<RestConfigurationDetails> list = new ArrayList<>();
        for (MethodSource<JavaClassSource> configureMethod : methods) {
            // there may be multiple route builder configure methods
            List<RestConfigurationDetails> details = parser.parseRestConfiguration(clazz, baseDir, fullyQualifiedFileName, configureMethod);
            list.addAll(details);
        }
        // we end up parsing bottom->up so reverse list
        Collections.reverse(list);

        return list;
    }

    /**
     * Parses the java source class and build a rest service model of the discovered rest services in the java source class.
     *
     * @param clazz                   the java source class
     * @param baseDir                 the base of the source code
     * @param fullyQualifiedFileName  the fully qualified source code file name
     * @return a list of rest services
     */
    public static List<RestServiceDetails> parseRestService(JavaClassSource clazz, String baseDir, String fullyQualifiedFileName,
                                                            boolean includeInlinedRouteBuilders) {

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

        CamelJavaRestDslParserHelper parser = new CamelJavaRestDslParserHelper();
        List<RestServiceDetails> list = new ArrayList<>();
        for (MethodSource<JavaClassSource> configureMethod : methods) {
            // there may be multiple route builder configure methods
            List<RestServiceDetails> details = parser.parseRestService(clazz, baseDir, fullyQualifiedFileName, configureMethod);
            list.addAll(details);
        }
        // we end up parsing bottom->up so reverse list
        Collections.reverse(list);

        return list;
    }

}
