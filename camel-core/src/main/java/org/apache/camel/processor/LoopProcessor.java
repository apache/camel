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
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The processor which sends messages in a loop.
 *
 * @version $Revision$
 */
public class LoopProcessor extends DelegateProcessor {
    public static final String PROP_ITER_COUNT = "CamelIterationCount";
    public static final String PROP_ITER_INDEX = "CamelIterationIndex";

    private static final Log LOG = LogFactory.getLog(LoopProcessor.class);

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
        if (value == null) {
            // TODO: we should probably catch evaluate/convert exception an set is as fault (after fix for CAMEL-316)
            throw new RuntimeCamelException("Expression \"" + expression + "\" does not evaluate to an int.");
        }
        int count = value.intValue();
        exchange.setProperty(PROP_ITER_COUNT, count);
        for (int i = 0; i < count; i++) {
            LOG.debug("LoopProcessor: iteration #" + i);
            exchange.setProperty(PROP_ITER_INDEX, i);
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
