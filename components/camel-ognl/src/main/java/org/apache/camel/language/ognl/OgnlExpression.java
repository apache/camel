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
package org.apache.camel.language.ognl;

import ognl.Ognl;
import ognl.OgnlContext;
import ognl.OgnlException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.impl.ExpressionSupport;
import org.apache.camel.language.ExpressionEvaluationException;
import org.apache.camel.language.IllegalSyntaxException;

import java.util.HashMap;
import java.util.Map;

/**
 * An <a href="http://www.ognl.org/">OGNL</a> {@link Expression}
 *
 * @version $Revision: $
 */
public class OgnlExpression extends ExpressionSupport<Exchange> {

    private final String expressionString;
    private final Class<?> type;
    private Object expression;

    public OgnlExpression(OgnlLanguage language, String expressionString, Class<?> type) {
        this.expressionString = expressionString;
        this.type = type;
        try {
            this.expression = Ognl.parseExpression(expressionString);
        } catch (OgnlException e) {
            throw new IllegalSyntaxException(language, expressionString);
        }
    }

    public static OgnlExpression ognl(String expression) {
        return new OgnlExpression(new OgnlLanguage(), expression, Object.class);
    }

    public Object evaluate(Exchange exchange) {
        // TODO we could use caching here but then we'd have possible
        // concurrency issues
        // so lets assume that the provider caches
        Map values = new HashMap();
        populateContext(values, exchange);
        OgnlContext oglContext = new OgnlContext();
        try {
            return Ognl.getValue(expression, oglContext, new RootObject(exchange));
        } catch (OgnlException e) {
            throw new ExpressionEvaluationException(this, exchange, e);
        }
    }

    protected void populateContext(Map map, Exchange exchange) {
        map.put("exchange", exchange);
        map.put("in", exchange.getIn());
        map.put("out", exchange.getOut());
    }

    protected String assertionFailureMessage(Exchange exchange) {
        return expressionString;
    }
}
