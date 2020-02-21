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
package org.apache.camel.reifier;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.processor.SendDynamicProcessor;
import org.apache.camel.spi.Language;
import org.apache.camel.util.Pair;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

public class ToDynamicReifier<T extends ToDynamicDefinition> extends ProcessorReifier<T> {

    public ToDynamicReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (T) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        String uri;
        Expression exp;
        if (definition.getEndpointProducerBuilder() != null) {
            uri = definition.getEndpointProducerBuilder().getUri();
            exp = definition.getEndpointProducerBuilder().expr(camelContext);
        } else {
            uri = StringHelper.notEmpty(definition.getUri(), "uri", this);
            exp = createExpression(uri);
        }

        SendDynamicProcessor processor = new SendDynamicProcessor(uri, exp);
        processor.setCamelContext(camelContext);
        processor.setPattern(parse(ExchangePattern.class, definition.getPattern()));
        if (definition.getCacheSize() != null) {
            processor.setCacheSize(parseInt(definition.getCacheSize()));
        }
        if (definition.getIgnoreInvalidEndpoint() != null) {
            processor.setIgnoreInvalidEndpoint(parseBoolean(definition.getIgnoreInvalidEndpoint(), false));
        }
        if  (definition.getAllowOptimisedComponents() != null) {
            processor.setAllowOptimisedComponents(parseBoolean(definition.getAllowOptimisedComponents(), true));
        }
        return processor;
    }

    protected Expression createExpression(String uri) {
        List<Expression> list = new ArrayList<>();

        String[] parts = safeSplitRaw(uri);
        for (String part : parts) {
            // the part may have optional language to use, so you can mix
            // languages
            String value = StringHelper.after(part, "language:");
            if (value != null) {
                String before = StringHelper.before(value, ":");
                String after = StringHelper.after(value, ":");
                if (before != null && after != null) {
                    // maybe its a language, must have language: as prefix
                    try {
                        Language partLanguage = camelContext.resolveLanguage(before);
                        if (partLanguage != null) {
                            Expression exp = partLanguage.createExpression(after);
                            list.add(exp);
                            continue;
                        }
                    } catch (NoSuchLanguageException e) {
                        // ignore
                    }
                }
            }
            // fallback and use simple language
            Language lan = camelContext.resolveLanguage("simple");
            Expression exp = lan.createExpression(part);
            list.add(exp);
        }

        Expression exp;
        if (list.size() == 1) {
            exp = list.get(0);
        } else {
            exp = ExpressionBuilder.concatExpression(list);
        }

        return exp;
    }

    // Utilities
    // -------------------------------------------------------------------------

    /**
     * We need to split the string safely for each + sign, but avoid splitting
     * within RAW(...).
     */
    private static String[] safeSplitRaw(String s) {
        List<String> list = new ArrayList<>();

        if (!s.contains("+")) {
            // no plus sign so there is only one part, so no need to split
            list.add(s);
        } else {
            // there is a plus sign so we need to split in a safe manner
            List<Pair<Integer>> rawPairs = URISupport.scanRaw(s);
            StringBuilder sb = new StringBuilder();
            char[] chars = s.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                char ch = chars[i];
                if (ch != '+' || URISupport.isRaw(i, rawPairs)) {
                    sb.append(ch);
                } else {
                    list.add(sb.toString());
                    sb.setLength(0);
                }
            }
            // any leftover?
            if (sb.length() > 0) {
                list.add(sb.toString());
                sb.setLength(0);
            }
        }

        return list.toArray(new String[list.size()]);
    }

}
