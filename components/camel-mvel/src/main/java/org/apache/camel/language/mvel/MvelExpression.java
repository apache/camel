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
package org.apache.camel.language.mvel;

import java.io.Serializable;

import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.support.ExpressionSupport;

public class MvelExpression extends ExpressionSupport {

    private final String expressionString;
    private final Class<?> type;
    private final Serializable compiled;

    public MvelExpression(MvelLanguage language, String expressionString, Class<?> type) {
        this.expressionString = expressionString;
        this.type = type;

        try {
            this.compiled = org.mvel2.MVEL.compileExpression(expressionString);
        } catch (Exception e) {
            throw new ExpressionIllegalSyntaxException(expressionString, e);
        }
    }

    public static MvelExpression mvel(String expression) {
        return new MvelExpression(new MvelLanguage(), expression, Object.class);
    }

    public <T> T evaluate(Exchange exchange, Class<T> tClass) {
        try {
            Object value = org.mvel2.MVEL.executeExpression(compiled, new RootObject(exchange));
            return exchange.getContext().getTypeConverter().convertTo(tClass, value);
        } catch (Exception e) {
            throw new ExpressionEvaluationException(this, exchange, e);
        }
    }

    public Object evaluate(Exchange exchange) {
        try {
            return org.mvel2.MVEL.executeExpression(compiled, new RootObject(exchange));
        } catch (Exception e) {
            throw new ExpressionEvaluationException(this, exchange, e);
        }
    }
    
    public Class<?> getType() {
        return type;
    }

    protected String assertionFailureMessage(Exchange exchange) {
        return expressionString;
    }

    @Override
    public String toString() {
        return "Mvel[" + expressionString + "]";
    }
}
