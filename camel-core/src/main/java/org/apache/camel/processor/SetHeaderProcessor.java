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
import org.apache.camel.Message;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A processor which sets the header on the IN or OUT message with an {@link org.apache.camel.Expression}
 */
public class SetHeaderProcessor extends ServiceSupport implements AsyncProcessor, Traceable, IdAware {
    private String id;
    private final Expression headerName;
    private final Expression expression;

    public SetHeaderProcessor(Expression headerName, Expression expression) {
        this.headerName = headerName;
        this.expression = expression;
        ObjectHelper.notNull(headerName, "headerName");
        ObjectHelper.notNull(expression, "expression");
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            Object newHeader = expression.evaluate(exchange, Object.class);

            if (exchange.getException() != null) {
                // the expression threw an exception so we should break-out
                callback.done(true);
                return true;
            }

            boolean out = exchange.hasOut();
            Message old = out ? exchange.getOut() : exchange.getIn();

            String key = headerName.evaluate(exchange, String.class);
            old.setHeader(key, newHeader);

        } catch (Throwable e) {
            exchange.setException(e);
        }

        callback.done(true);
        return true;
    }

    @Override
    public String toString() {
        return "SetHeader(" + headerName + ", " + expression + ")";
    }

    public String getTraceLabel() {
        return "setHeader[" + headerName + ", " + expression + "]";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getHeaderName() {
        return headerName.toString();
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    protected void doStart() throws Exception {
        //noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}