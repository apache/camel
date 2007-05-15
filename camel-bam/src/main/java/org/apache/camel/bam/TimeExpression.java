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
package org.apache.camel.bam;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;

/**
 * 
 * @version $Revision: $
 */
public class TimeExpression implements Expression<Exchange> {

    private org.apache.camel.bam.Activity activity;
    private Expression expression;


    public TimeExpression(org.apache.camel.bam.Activity activity, Expression expression) {
        this.activity = activity;
        this.expression = expression;
    }


    public Object evaluate(Exchange exchange) {
        return expression.evaluate(exchange);
    }

    /**
     * Creates a new temporal rule on this expression and the other expression
     */
    public TemporalRule after(TimeExpression expression) {
        return new TemporalRule(this, expression);
    }
}
