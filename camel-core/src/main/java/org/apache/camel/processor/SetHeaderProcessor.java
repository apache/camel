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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A processor which sets the header on the IN message
 */
public class SetHeaderProcessor extends DelegateProcessor implements Processor {
    private static final transient Log LOG = LogFactory.getLog(SetHeaderProcessor.class);
    private String name;
    private Expression expression;

    public SetHeaderProcessor(String name, Expression expression) {
        this.name = name;
        this.expression = expression;
    }

    public SetHeaderProcessor(String name, Expression expression,
            Processor childProcessor) {
        super(childProcessor);
        this.name = name;
        this.expression = expression;
    }

    public void process(Exchange exchange) throws Exception {
        Object value = expression.evaluate(exchange);
        if (value == null) {
            LOG.warn("Expression: " + expression
                    + " on exchange: " + exchange + " evaluated to null.");
        }
        exchange.getIn().setHeader(name, value);
        super.process(exchange);
    }

    @Override
    public String toString() {
        return "setHeader(" + name + ", " + expression + ")";
    }
}
