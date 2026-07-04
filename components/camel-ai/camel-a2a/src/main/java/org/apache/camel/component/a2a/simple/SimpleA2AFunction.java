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
package org.apache.camel.component.a2a.simple;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;

import static org.apache.camel.language.simple.ast.SimpleFunctionExpression.ifStartsWithReturnRemainder;

/**
 * {@link SimpleLanguageFunctionFactory} for the {@code a2a:} Simple language function namespace.
 * <p>
 * Supports the following functions:
 * <ul>
 * <li>{@code ${a2a:emit(message)}} — emit a progress update with WORKING state</li>
 * <li>{@code ${a2a:emit(STATE, message)}} — emit a progress update with explicit state</li>
 * <li>{@code ${a2a:text}} — extract TextPart content from body</li>
 * <li>{@code ${a2a:text(expression)}} — extract TextPart content from expression result</li>
 * <li>{@code ${a2a:data}} — extract DataPart content from body</li>
 * <li>{@code ${a2a:data(expression)}} — extract DataPart content from expression result</li>
 * <li>{@code ${a2a:file}} — extract FilePart content from body</li>
 * <li>{@code ${a2a:file(expression)}} — extract FilePart content from expression result</li>
 * <li>{@code ${a2a:card}} — full agent card as JSON string</li>
 * <li>{@code ${a2a:card.name}} — agent card name</li>
 * <li>{@code ${a2a:card.description}} — agent card description</li>
 * <li>{@code ${a2a:card.url}} — agent card URL</li>
 * <li>{@code ${a2a:card.version}} — agent card version</li>
 * <li>{@code ${a2a:card.skills}} — skills formatted as text (one per line)</li>
 * <li>{@code ${a2a:card.skills.json}} — skills as JSON array</li>
 * </ul>
 */
@JdkService(SimpleLanguageFunctionFactory.FACTORY + "/camel-a2a")
public class SimpleA2AFunction implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        // ${a2a:emit(message)} or ${a2a:emit(STATE, message)}
        String remainder = ifStartsWithReturnRemainder("a2a:emit(", function);
        if (remainder != null) {
            String args = StringHelper.beforeLast(remainder, ")");
            if (args == null || args.isEmpty()) {
                return null;
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(args, ',', true, true);
            if (tokens.length == 1) {
                // Single arg: message only, WORKING implied
                String message = StringHelper.removeLeadingAndEndingQuotes(tokens[0].trim());
                return A2ASimpleExpressionBuilder.emitProgress(null, message);
            } else if (tokens.length == 2) {
                // Two args: state, message
                String state = StringHelper.removeLeadingAndEndingQuotes(tokens[0].trim());
                String message = StringHelper.removeLeadingAndEndingQuotes(tokens[1].trim());
                return A2ASimpleExpressionBuilder.emitProgress(state, message);
            }
            return null;
        }

        // ${a2a:text} or ${a2a:text(expression)}
        if ("a2a:text".equals(function)) {
            return A2ASimpleExpressionBuilder.extractText(null);
        }
        remainder = ifStartsWithReturnRemainder("a2a:text(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return A2ASimpleExpressionBuilder.extractText(exp);
        }

        // ${a2a:data} or ${a2a:data(expression)}
        if ("a2a:data".equals(function)) {
            return A2ASimpleExpressionBuilder.extractData(null);
        }
        remainder = ifStartsWithReturnRemainder("a2a:data(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return A2ASimpleExpressionBuilder.extractData(exp);
        }

        // ${a2a:file} or ${a2a:file(expression)}
        if ("a2a:file".equals(function)) {
            return A2ASimpleExpressionBuilder.extractFile(null);
        }
        remainder = ifStartsWithReturnRemainder("a2a:file(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return A2ASimpleExpressionBuilder.extractFile(exp);
        }

        // ${a2a:card} or ${a2a:card.<field>}
        if ("a2a:card".equals(function)) {
            return A2ASimpleExpressionBuilder.extractCardField(null);
        }
        remainder = ifStartsWithReturnRemainder("a2a:card.", function);
        if (remainder != null) {
            return A2ASimpleExpressionBuilder.extractCardField(remainder);
        }

        return null;
    }
}
