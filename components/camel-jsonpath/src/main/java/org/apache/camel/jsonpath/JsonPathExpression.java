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
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.support.ExpressionAdapter;

public class JsonPathExpression extends ExpressionAdapter {

    private final String expression;
    private Class<?> resultType;
    private final JsonPathEngine engine;

    public JsonPathExpression(String expression) {
        this.expression = expression;
        try {
            engine = new JsonPathEngine(expression);
        } catch (Exception e) {
            throw new ExpressionIllegalSyntaxException(expression, e);
        }
    }

    public Class<?> getResultType() {
        return resultType;
    }

    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    @Override
    public Object evaluate(Exchange exchange) {
        try {
            Object result = evaluateJsonPath(exchange, engine);
            if (resultType != null) {
                return exchange.getContext().getTypeConverter().convertTo(resultType, exchange, result);
            } else {
                return result;
            }
        } catch (Exception e) {
            throw new ExpressionEvaluationException(this, exchange, e);
        }
    }

    @Override
    public String toString() {
        return "jsonpath[" + expression + "]";
    }

    private Object evaluateJsonPath(Exchange exchange, JsonPathEngine engine) throws Exception {
        return engine.read(exchange);
    }

}
