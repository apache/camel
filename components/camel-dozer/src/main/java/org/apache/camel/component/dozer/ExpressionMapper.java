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
package org.apache.camel.component.dozer;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.spi.Language;

/**
 * Provides support for mapping a Camel expression to a target field in a 
 * mapping.  Expressions have the following format:
 * <br><br>
 * [language]:[expression]
 * <br><br>
 */
public class ExpressionMapper extends BaseConverter {
    
    private ThreadLocal<Exchange> currentExchange = new ThreadLocal<Exchange>();
    
    @Override
    public Object convert(Object existingDestinationFieldValue, 
            Object sourceFieldValue, 
            Class<?> destinationClass,
            Class<?> sourceClass) {
        try {
            if (currentExchange.get() == null) {
                throw new IllegalStateException(
                        "Current exchange has not been set for ExpressionMapper");
            }
            // Resolve the language being used for this expression and evaluate
            Exchange exchange = currentExchange.get();
            Language expLang = exchange.getContext().resolveLanguage(getLanguagePart());
            Expression exp = expLang.createExpression(getExpressionPart());
            return exp.evaluate(exchange, destinationClass);
        } finally {
            done();
        }
    }
    
    /**
     * Used as the source field for Dozer mappings. 
     */
    public String getExpression() {
        return getParameter();
    }
    
    /**
     * The actual expression, without the language prefix.
     */
    public String getExpressionPart() {
        return getParameter().substring(getParameter().indexOf(":") + 1);
    }
    
    /**
     * The expression language used for this mapping.
     */
    public String getLanguagePart() {
        return getParameter().substring(0, getParameter().indexOf(":"));
    }
    
    /**
     * Sets the Camel exchange reference for this mapping.  The exchange 
     * reference is stored in a thread-local which is cleaned up after the 
     * mapping has been performed via the done() method.
     * @param exchange
     */
    public void setCurrentExchange(Exchange exchange) {
        currentExchange.set(exchange);
    }
}
