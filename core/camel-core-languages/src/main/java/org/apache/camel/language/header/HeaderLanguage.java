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
package org.apache.camel.language.header;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.builder.ExpressionBuilder;

/**
 * A language for header expressions.
 */
@Language("header")
@BodyTypes(expressionOut = @BodyType(value = Carrier.UNKNOWN,
                                     description = "The value of the header as it is, null when there is none. A missing header"
                                                   + " falls back to the exchange property of that name"))
public class HeaderLanguage extends LanguageSupport {

    public static Expression header(String headerName) {
        return ExpressionBuilder.headerExpression(headerName);
    }

    @Override
    public Predicate createPredicate(String expression) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression));
    }

    @Override
    public Expression createExpression(String expression) {
        if (expression != null && isStaticResource(expression)) {
            expression = loadResource(expression);
        }
        return HeaderLanguage.header(expression);
    }
}
