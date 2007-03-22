/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import static org.apache.camel.util.ObjectHelper.notNull;

import java.util.Iterator;

/**
 * Implements a dynamic <a href="http://activemq.apache.org/camel/recipient-list.html">Recipient List</a> pattern
 * where the list of actual endpoints to send a message exchange to are dependent on some dynamic expression.
 *
 * @version $Revision$
 */
public class RecipientList<E extends Exchange> implements Processor<E> {
    private final Expression<E> expression;

    public RecipientList(Expression<E> expression) {
        notNull(expression, "expression");
        this.expression = expression;
    }

    @Override
    public String toString() {
        return "RecipientList[" + expression + "]";
    }

    public void onExchange(E exchange) {
        Object receipientList = expression.evaluate(exchange);
        Iterator iter = ObjectHelper.iterator(receipientList);
        while (iter.hasNext()) {
            Object recipient = iter.next();
            Endpoint<E> endpoint = resolveEndpoint(exchange, recipient);
            endpoint.onExchange(exchange);
        }
    }

    protected Endpoint<E> resolveEndpoint(E exchange, Object recipient) {
        return ExchangeHelper.resolveEndpoint(exchange, recipient);
    }
}
