/**
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
package org.apache.camel.jsonpath;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.Predicate;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.LanguageSupport;

public class JsonPathLanguage extends LanguageSupport {

    @Override
    public Predicate createPredicate(final String predicate) {
        final JSonPathEngine engine;
        try {
            engine = new JSonPathEngine(predicate);
        } catch (Exception e) {
            throw new ExpressionIllegalSyntaxException(predicate, e);
        }

        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                try {
                    return evaluateJsonPath(exchange, engine);
                } catch (Exception e) {
                    throw new ExpressionEvaluationException(this, exchange, e);
                }
            }

            @Override
            public String toString() {
                return "jsonpath[" + predicate + "]";
            }
        };
    }

    @Override
    public Expression createExpression(final String expression) {
        final JSonPathEngine engine;
        try {
            engine = new JSonPathEngine(expression);
        } catch (Exception e) {
            throw new ExpressionIllegalSyntaxException(expression, e);
        }

        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                try {
                    return evaluateJsonPath(exchange, engine);
                } catch (Exception e) {
                    throw new ExpressionEvaluationException(this, exchange, e);
                }
            }

            @Override
            public String toString() {
                return "jsonpath[" + expression + "]";
            }
        };
    }

    private Object evaluateJsonPath(Exchange exchange, JSonPathEngine engine) throws Exception {
        return engine.read(exchange);
    }

}
