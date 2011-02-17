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
package org.apache.camel.processor.idempotent;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeCamelException;

/**
 * An exception thrown if no message ID could be found on a message which is to be used with the
 * <a href="http://camel.apache.org/idempotent-consumer.html">Idempotent Consumer</a> pattern.
 *
 * @version 
 */
public class NoMessageIdException extends RuntimeCamelException {
    private static final long serialVersionUID = 5755929795399134568L;

    private final Exchange exchange;
    private final Expression expression;

    public NoMessageIdException(Exchange exchange, Expression expression) {
        super("No message ID could be found using expression: " + expression + " on message exchange: " + exchange);
        this.exchange = exchange;
        this.expression = expression;
    }

    /**
     * The exchange which caused this failure
     */
    public Exchange getExchange() {
        return exchange;
    }

    /**
     * The expression which was used
     */
    public Expression getExpression() {
        return expression;
    }
}
