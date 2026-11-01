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
package org.apache.camel.language.simple.functions;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.CollectionExpressionBuilder;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;

import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;
import static org.apache.camel.language.simple.SimpleFunctionHelper.trimArgument;

/**
 * Built-in Simple function: {@code ${join()}} / {@code ${join(separator)}} / {@code ${join(separator,prefix)}} /
 * {@code ${join(separator,prefix,expression)}}.
 * <p>
 * The white space around the prefix and the expression is not part of them, as for the arguments of the other
 * functions. An unquoted separator is kept as written, as it can be white space on purpose: {@code ${join(; )}} joins
 * with {@code "; "}; a quoted one is the text inside its quotes.
 */
public final class JoinFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        String remainder = ifStartsWithReturnRemainder("join(", function);
        if (remainder == null) {
            return null;
        }
        String values = StringHelper.beforeLast(remainder, ")");
        String separator = ",";
        String prefix = null;
        String exp = "${body}";
        if (ObjectHelper.isNotEmpty(values)) {
            // the arguments as written (with their quotes), split at the commas outside quotes as splitArguments does,
            // so the separator can be kept as written
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false, true);
            if (tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${join(separator,prefix,expression)} was: " + function, index);
            }
            separator = StringHelper.removeLeadingAndEndingQuotes(tokens[0]);
            if (tokens.length > 1) {
                // the prefix and the expression without the white space and the quotes around them
                prefix = trimArgument(tokens[1]);
                if (tokens.length == 3) {
                    exp = trimArgument(tokens[2]);
                }
            }
        }
        return CollectionExpressionBuilder.joinExpression(exp, separator, prefix);
    }
}
