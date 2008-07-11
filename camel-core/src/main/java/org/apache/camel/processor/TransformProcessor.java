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
 * A processor which sets the body on the OUT message with an expression
 */
public class TransformProcessor extends DelegateProcessor implements Processor {
    private static final transient Log LOG = LogFactory.getLog(TransformProcessor.class);
    private Expression expression;

    public TransformProcessor(Expression expression) {
        this.expression = expression;
    }

    public TransformProcessor(Expression expression, Processor childProcessor) {
        super(childProcessor);
        this.expression = expression;
    }

    public void process(Exchange exchange) throws Exception {
        Object newBody = expression.evaluate(exchange);
        exchange.getOut().setBody(newBody);

        // propogate headers
        exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());
        
        super.process(exchange);
    }

    @Override
    public String toString() {
        return "transform(" + expression + ")";
    }
}
