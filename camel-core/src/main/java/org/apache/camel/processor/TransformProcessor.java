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
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A processor which sets the body on the OUT message with an {@link Expression}.
 */
public class TransformProcessor extends ServiceSupport implements AsyncProcessor, Traceable, IdAware {
    private String id;
    private final Expression expression;

    public TransformProcessor(Expression expression) {
        ObjectHelper.notNull(expression, "expression", this);
        this.expression = expression;
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            Object newBody = expression.evaluate(exchange, Object.class);

            if (exchange.getException() != null) {
                // the expression threw an exception so we should break-out
                callback.done(true);
                return true;
            }

            boolean out = exchange.hasOut();
            Message old = out ? exchange.getOut() : exchange.getIn();

            // create a new message container so we do not drag specialized message objects along
            // but that is only needed if the old message is a specialized message
            boolean copyNeeded = !(old.getClass().equals(DefaultMessage.class));

            if (copyNeeded) {
                Message msg = new DefaultMessage(exchange.getContext());
                msg.copyFromWithNewBody(old, newBody);

                // replace message on exchange (must set as OUT)
                ExchangeHelper.replaceMessage(exchange, msg, true);
            } else {
                // no copy needed so set replace value directly
                old.setBody(newBody);

                // but the message must be on OUT
                if (!exchange.hasOut()) {
                    exchange.setOut(exchange.getIn());
                }
            }

        } catch (Throwable e) {
            exchange.setException(e);
        }

        callback.done(true);
        return true;
    }

    @Override
    public String toString() {
        return "Transform(" + expression + ")";
    }

    public String getTraceLabel() {
        return "transform[" + expression + "]";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
