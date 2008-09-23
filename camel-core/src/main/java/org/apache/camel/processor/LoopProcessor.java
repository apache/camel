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
package org.apache.camel.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.util.ExchangeHelper;

/**
 * The processor which sends messages in a loop.
 *
 * @version $Revision: $
 */
public class LoopProcessor extends DelegateProcessor {
    private Expression<Exchange> expression;

    public LoopProcessor(Expression<Exchange> expression, Processor processor) {
        super(processor);
        this.expression = expression;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // Intermediate conversion to String is needed when direct conversion to Integer is not available
        // but evaluation result is a textual representation of a numeric value.
        String text = ExchangeHelper.convertToType(exchange, String.class, expression.evaluate(exchange));
        Integer value = ExchangeHelper.convertToType(exchange, Integer.class, text);
        int count = value != null ? value.intValue() : 0;
        while (count-- > 0) {
            super.process(exchange);
        }
    }

    @Override
    public String toString() {
        return "Loop[for: " + expression + " times do: " + getProcessor() + "]";
    }

    public Expression<Exchange> getExpression() {
        return expression;
    }
}
