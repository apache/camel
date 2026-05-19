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
package org.apache.camel.language.simple;

import java.util.List;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.functions.CollateFunctionFactory;
import org.apache.camel.language.simple.functions.JoinFunctionFactory;
import org.apache.camel.language.simple.functions.RandomFunctionFactory;
import org.apache.camel.language.simple.functions.SkipFunctionFactory;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.support.ResolverHelper;

import static org.apache.camel.language.simple.ast.SimpleFunctionExpression.ifStartsWithReturnRemainder;

/**
 * Dispatches Simple/CSimple function lookup to built-in function factories and to {@link SimpleLanguageFunctionFactory}
 * implementations shipped by external Camel components (currently camel-attachments, camel-base64, camel-jsoup).
 * <p>
 * Each entry carries a gate that decides whether its factory is consulted for a given function string; gates mirror the
 * inline checks that previously lived in {@code SimpleFunctionExpression}, so a function belonging to a missing
 * component still surfaces the "add the JAR to your classpath" error from
 * {@link ResolverHelper#resolveMandatoryBootstrapService}. Factory resolution is cached by the bootstrap factory
 * finder, so repeated dispatch is cheap.
 */
public final class SimpleFunctionDispatcher {

    /**
     * Built-in factories shipped by camel-core-languages itself. Iterated before {@link #EXPRESSION_ENTRIES}, matching
     * the original priority of these functions inside {@code SimpleFunctionExpression}. Each factory returns
     * {@code null} for inputs it does not recognise, so no gating predicate is needed.
     */
    private static final List<SimpleLanguageFunctionFactory> BUILT_INS = List.of(
            new RandomFunctionFactory(),
            new SkipFunctionFactory(),
            new CollateFunctionFactory(),
            new JoinFunctionFactory());

    private static final List<Entry> EXPRESSION_ENTRIES = List.of(
            new Entry("camel-attachments", SimpleFunctionDispatcher::isAttachmentFunction),
            new Entry("camel-base64", SimpleFunctionDispatcher::isBase64Function),
            new Entry("camel-jsoup", SimpleFunctionDispatcher::isHtmlFunction));

    /**
     * Code-generation entries exclude camel-jsoup deliberately: it has no csimple support and its {@code createCode}
     * throws.
     */
    private static final List<Entry> CODE_ENTRIES = List.of(
            new Entry("camel-attachments", SimpleFunctionDispatcher::isAttachmentFunction),
            new Entry("camel-base64", SimpleFunctionDispatcher::isBase64Function));

    private SimpleFunctionDispatcher() {
    }

    public static Expression tryCreate(CamelContext camelContext, String function, int index) {
        Expression answer = tryCreateBuiltIn(camelContext, function, index);
        if (answer != null) {
            return answer;
        }
        return tryCreateExternal(camelContext, function, index);
    }

    public static Expression tryCreateBuiltIn(CamelContext camelContext, String function, int index) {
        for (SimpleLanguageFunctionFactory factory : BUILT_INS) {
            Expression answer = factory.createFunction(camelContext, function, index);
            if (answer != null) {
                return answer;
            }
        }
        return null;
    }

    public static Expression tryCreateExternal(CamelContext camelContext, String function, int index) {
        for (Entry entry : EXPRESSION_ENTRIES) {
            if (!entry.claims.test(function)) {
                continue;
            }
            SimpleLanguageFunctionFactory factory = resolve(camelContext, entry.jarName);
            Expression answer = factory.createFunction(camelContext, function, index);
            if (answer != null) {
                return answer;
            }
        }
        return null;
    }

    public static String tryCreateCode(CamelContext camelContext, String function, int index) {
        String code = tryCreateCodeBuiltIn(camelContext, function, index);
        if (code != null) {
            return code;
        }
        return tryCreateCodeExternal(camelContext, function, index);
    }

    public static String tryCreateCodeBuiltIn(CamelContext camelContext, String function, int index) {
        for (SimpleLanguageFunctionFactory factory : BUILT_INS) {
            @SuppressWarnings("deprecation")
            String code = factory.createCode(camelContext, function, index);
            if (code != null) {
                return code;
            }
        }
        return null;
    }

    public static String tryCreateCodeExternal(CamelContext camelContext, String function, int index) {
        for (Entry entry : CODE_ENTRIES) {
            if (!entry.claims.test(function)) {
                continue;
            }
            SimpleLanguageFunctionFactory factory = resolve(camelContext, entry.jarName);
            @SuppressWarnings("deprecation")
            String code = factory.createCode(camelContext, function, index);
            if (code != null) {
                return code;
            }
        }
        return null;
    }

    private static SimpleLanguageFunctionFactory resolve(CamelContext camelContext, String jarName) {
        return ResolverHelper.resolveMandatoryBootstrapService(
                camelContext,
                SimpleLanguageFunctionFactory.FACTORY + "/" + jarName,
                SimpleLanguageFunctionFactory.class,
                jarName);
    }

    private static boolean isAttachmentFunction(String function) {
        return "attachments".equals(function)
                || "clearAttachments".equals(function)
                || ifStartsWithReturnRemainder("setAttachment", function) != null
                || ifStartsWithReturnRemainder("attachment", function) != null;
    }

    private static boolean isBase64Function(String function) {
        return "base64Encode".equals(function)
                || "base64Decode".equals(function)
                || ifStartsWithReturnRemainder("base64Encode", function) != null
                || ifStartsWithReturnRemainder("base64Decode", function) != null;
    }

    private static boolean isHtmlFunction(String function) {
        return "htmlClean".equals(function)
                || "htmlParse".equals(function)
                || "htmlDecode".equals(function)
                || ifStartsWithReturnRemainder("htmlClean", function) != null
                || ifStartsWithReturnRemainder("htmlParse", function) != null
                || ifStartsWithReturnRemainder("htmlDecode", function) != null;
    }

    private static final class Entry {

        private final String jarName;
        private final Predicate<String> claims;

        private Entry(String jarName, Predicate<String> claims) {
            this.jarName = jarName;
            this.claims = claims;
        }
    }
}
