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
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "eval-language", displayName = "Evaluate Language", description = "Evaluate Language and display result")
public class EvalLanguageDevConsole extends AbstractDevConsole {

    /**
     * The language to use
     */
    public static final String LANGUAGE = "language";

    /**
     * Template to use for executing simple language function
     */
    public static final String TEMPLATE = "template";

    /**
     * Whether to execute as predicate (use expression by default)
     */
    public static final String PREDICATE = "predicate";

    /**
     * Optional message body
     */
    public static final String BODY = "body";

    /**
     * Optional message headers
     */
    public static final String HEADERS = "headers";

    public EvalLanguageDevConsole() {
        super("camel", "eval-language", "Evaluate Language", "Evaluate Language and display result");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        String language = (String) options.getOrDefault(LANGUAGE, "simple");
        String template = (String) options.get(TEMPLATE);
        if (template != null) {
            Exchange dummy = new DefaultExchange(getCamelContext());
            dummy.getMessage().setBody(options.get(BODY));
            var headers = options.get(HEADERS);
            if (headers instanceof Map map) {
                dummy.getMessage().setHeaders(map);
            }

            String out;
            boolean predicate = options.getOrDefault(PREDICATE, "false").equals("true");
            if (predicate) {
                Predicate pre = getCamelContext().resolveLanguage(language).createPredicate(template);
                out = pre.matches(dummy) ? "true" : "false";
            } else {
                Expression exp = getCamelContext().resolveLanguage(language).createExpression(template);
                out = exp.evaluate(dummy, String.class);
            }
            sb.append(String.format("%nEvaluating (%s): %s", language, template));
            sb.append("\n");
            sb.append(out);
        }
        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        String language = (String) options.getOrDefault(LANGUAGE, "simple");
        String template = (String) options.get(TEMPLATE);
        if (template != null) {
            Exchange dummy = new DefaultExchange(getCamelContext());
            dummy.getMessage().setBody(options.get(BODY));
            var headers = options.get(HEADERS);
            if (headers instanceof Map map) {
                dummy.getMessage().setHeaders(map);
            }

            Exception cause = null;
            String out = null;
            try {
                boolean predicate = options.getOrDefault(PREDICATE, "false").equals("true");
                if (predicate) {
                    Predicate pre = getCamelContext().resolveLanguage(language).createPredicate(template);
                    out = pre.matches(dummy) ? "true" : "false";
                } else {
                    Expression exp = getCamelContext().resolveLanguage(language).createExpression(template);
                    out = exp.evaluate(dummy, String.class);
                }
            } catch (Exception e) {
                cause = e;
            }

            if (cause != null) {
                root.put("status", "failed");
                root.put("exception",
                        MessageHelper.dumpExceptionAsJSonObject(cause).getMap("exception"));
            } else {
                root.put("status", "success");
                root.put("result", out);
            }
        }

        return root;
    }
}
