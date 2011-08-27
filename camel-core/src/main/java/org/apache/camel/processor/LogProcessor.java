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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.util.AsyncProcessorHelper;

/**
 * A processor which evaluates an Expression and logs it.
 *
 * @version 
 */
public class LogProcessor implements AsyncProcessor, Traceable {

    private final Expression expression;
    private final CamelLogger logger;

    public LogProcessor(Expression expression, CamelLogger logger) {
        this.expression = expression;
        this.logger = logger;
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            String msg = expression.evaluate(exchange, String.class);
            logger.log(msg);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            // callback must be invoked
            callback.done(true);
        }
        return true;
    }

    @Override
    public String toString() {
        return "Log[" + expression + "]";
    }

    public String getTraceLabel() {
        return "log[" + expression + "]";
    }
}
