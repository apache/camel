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
package org.apache.camel.language.jev;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.jev.JevEndpoint;
import org.apache.camel.component.jev.JevPredicate;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.PredicateToExpressionAdapter;

/** Evaluates a configured Jev endpoint's Noul question as a boolean expression or predicate. */
@Language("jev")
public class JevLanguage extends LanguageSupport {
    @Override
    public Predicate createPredicate(String expression) {
        try {
            JevEndpoint endpoint = getCamelContext().getEndpoint(expression, JevEndpoint.class);
            JevPredicate predicate = endpoint.createConfiguredPredicate();
            predicate.init(getCamelContext());
            return predicate;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public Expression createExpression(String expression) {
        return PredicateToExpressionAdapter.toExpression(createPredicate(expression));
    }
}
