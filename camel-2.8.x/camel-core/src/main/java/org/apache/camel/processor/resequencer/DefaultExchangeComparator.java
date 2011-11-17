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
package org.apache.camel.processor.resequencer;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;

/**
 * Compares elements of an {@link Exchange} sequence by comparing
 * <code>long</code> values returned by this comaprator's
 * <code>expression</code>.
 * 
 * @version 
 */
public class DefaultExchangeComparator implements ExpressionResultComparator {

    private Expression expression;

    public void setExpression(Expression expression) {
        this.expression = expression;
    }

    public boolean predecessor(Exchange o1, Exchange o2) {
        long n1 = getSequenceNumber(o1);
        long n2 = getSequenceNumber(o2);
        return n1 == (n2 - 1L);
    }

    public boolean successor(Exchange o1, Exchange o2) {
        long n1 = getSequenceNumber(o1);
        long n2 = getSequenceNumber(o2);
        return n2 == (n1 - 1L);
    }

    public int compare(Exchange o1, Exchange o2) {
        Long n1 = getSequenceNumber(o1);
        Long n2 = getSequenceNumber(o2);
        return n1.compareTo(n2);
    }

    private long getSequenceNumber(Exchange exchange) {
        return expression.evaluate(exchange, Long.class);
    }
}
