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
package org.apache.camel.language;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeCamelException;

/**
 * An exception thrown if evaluation of the expression failed.
 *
 * @version $Revision$
 */
public class ExpressionEvaluationException extends RuntimeCamelException {
    private final Expression<Exchange> expression;
    private final Exchange exchange;

    public ExpressionEvaluationException(Expression<Exchange> expression, Exchange exchange, Throwable cause) {
        super(cause);
        this.expression = expression;
        this.exchange = exchange;
    }

    public Expression<Exchange> getExpression() {
        return expression;
    }

    public Exchange getExchange() {
        return exchange;
    }
    
}
