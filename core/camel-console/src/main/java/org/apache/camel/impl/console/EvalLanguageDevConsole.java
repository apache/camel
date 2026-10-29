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
package org.apache.camel.impl.console;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "eval-language", displayName = "Evaluate Language", description = "Evaluate Language and display result",
            readOnly = false)
public class EvalLanguageDevConsole extends AbstractDevConsole {

    public record Response(
            @Metadata(description = "The evaluation status, success or failed (only present when a template was given)") String status,
            @Metadata(description = "The evaluation result (only present on success)") String result,
            @Metadata(description = "The exception, as an opaque JSON object (only present on failure)") Map<String, Object> exception) {
    }

    @Metadata(label = "query", description = "The language to use", javaType = "java.lang.String", defaultValue = "simple")
    public static final String LANGUAGE = "language";

    @Metadata(label = "query", description = "Template to use for executing simple language function",
              javaType = "java.lang.String")
    public static final String TEMPLATE = "template";

    @Metadata(label = "query", description = "Whether to execute as predicate (use expression by default)",
              javaType = "java.lang.Boolean", defaultValue = "false")
    public static final String PREDICATE = "predicate";

    @Metadata(label = "query", description = "Optional message body", javaType = "java.lang.String")
    public static final String BODY = "body";

    @Metadata(label = "query", description = "Optional message headers", javaType = "java.lang.String")
    public static final String HEADERS = "headers";

    @Metadata(label = "query", description = "Optional exchange variables", javaType = "java.lang.String")
    public static final String VARIABLES = "variables";

    @Metadata(label = "query",
              description = "Optional type of the result, as a class name such as java.lang.String, passed to the language"
                            + " as its resultType option, as in a route (not used for a predicate)",
              javaType = "java.lang.String")
    public static final String RESULT_TYPE = "resultType";

    public EvalLanguageDevConsole() {
        super("camel", "eval-language", "Evaluate Language", "Evaluate Language and display result");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        String language = optionString(options, LANGUAGE);
        if (language == null) {
            language = "simple";
        }
        String template = optionString(options, TEMPLATE);
        if (template != null) {
            Exchange dummy = new DefaultExchange(getCamelContext());
            dummy.getMessage().setBody(options.get(BODY));
            var headers = options.get(HEADERS);
            if (headers instanceof Map map) {
                dummy.getMessage().setHeaders(map);
            }
            var variables = options.get(VARIABLES);
            if (variables instanceof Map map2) {
                map2.forEach((k, v) -> dummy.setVariable(k.toString(), v));
            }

            String out;
            try {
                boolean predicate = optionBoolean(options, PREDICATE, false);
                template = resolvePlaceholders(template);
                if (predicate) {
                    Predicate pre = getCamelContext().resolveLanguage(language).createPredicate(template);
                    out = pre.matches(dummy) ? "true" : "false";
                } else {
                    Expression exp = createExpression(language, template, options);
                    out = exp.evaluate(dummy, String.class);
                }
            } catch (Exception e) {
                out = "Error: " + e.getMessage();
            }
            sb.append(String.format("%nEvaluating (%s): %s", language, template));
            sb.append("\n");
            sb.append(out);
        }
        return sb.toString();
    }

    /**
     * The expression, with the resultType option of the language when one is given, as a route passes it: an XPath
     * function such as string() or count() then gives its value, where the default result of xpath is nodes.
     */
    private Expression createExpression(String language, String template, Map<String, Object> options) {
        Language lan = getCamelContext().resolveLanguage(language);
        String resultType = optionString(options, RESULT_TYPE);
        if (resultType == null || resultType.isBlank()) {
            return lan.createExpression(template);
        }
        Class<?> type = getCamelContext().getClassResolver().resolveClass(resultType.strip());
        if (type == null) {
            throw new IllegalArgumentException("resultType " + resultType + " is not a class on the classpath");
        }
        return lan.createExpression(template, new Object[] { type });
    }

    /**
     * Resolves property placeholders in the template first, as the route loaders do before a language parses the text,
     * so that {{myPeriod}} or ${body} > {{myThreshold}} evaluate as they would in a route (CAMEL-24698).
     */
    private String resolvePlaceholders(String template) {
        if (template != null && template.contains("{{")) {
            return getCamelContext().resolvePropertyPlaceholders(template);
        }
        return template;
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String language = optionString(options, LANGUAGE);
        if (language == null) {
            language = "simple";
        }

        String status = null;
        String result = null;
        Map<String, Object> exception = null;

        String template = optionString(options, TEMPLATE);
        if (template != null) {
            Exchange dummy = new DefaultExchange(getCamelContext());
            dummy.getMessage().setBody(options.get(BODY));
            var headers = options.get(HEADERS);
            if (headers instanceof Map map) {
                dummy.getMessage().setHeaders(map);
            }
            var variables = options.get(VARIABLES);
            if (variables instanceof Map map2) {
                map2.forEach((k, v) -> dummy.setVariable(k.toString(), v));
            }

            Exception cause = null;
            String out = null;
            try {
                boolean predicate = optionBoolean(options, PREDICATE, false);
                template = resolvePlaceholders(template);
                if (predicate) {
                    Predicate pre = getCamelContext().resolveLanguage(language).createPredicate(template);
                    out = pre.matches(dummy) ? "true" : "false";
                } else {
                    Expression exp = createExpression(language, template, options);
                    out = exp.evaluate(dummy, String.class);
                }
            } catch (Exception e) {
                cause = e;
            }

            if (cause != null) {
                status = "failed";
                exception = MessageHelper.dumpExceptionAsJSonObject(cause).getMap("exception");
            } else {
                status = "success";
                result = out;
            }
        }

        Response response = new Response(status, result, exception);
        return JsonRecordSupport.toJsonObject(response);
    }
}
