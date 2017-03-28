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

import java.util.Comparator;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;

/**
 * A processor that sorts the expression using a comparator
 */
public class SortProcessor<T> extends ServiceSupport implements AsyncProcessor, Traceable, IdAware {

    private String id;
    private final Expression expression;
    private final Comparator<? super T> comparator;

    public SortProcessor(Expression expression, Comparator<? super T> comparator) {
        this.expression = expression;
        this.comparator = comparator;
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            Message in = exchange.getIn();

            @SuppressWarnings("unchecked")
            List<T> list = expression.evaluate(exchange, List.class);
            list.sort(comparator);

            if (exchange.getPattern().isOutCapable()) {
                Message out = exchange.getOut();
                out.copyFromWithNewBody(in, list);
            } else {
                in.setBody(list);
            }
        } catch (Exception e) {
            exchange.setException(e);
        }

        callback.done(true);
        return true;
    }

    public String toString() {
        return "Sort[" + expression + "]";
    }

    @Override
    public String getTraceLabel() {
        return "sort[" + expression + "]";
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

    public Comparator<? super T> getComparator() {
        return comparator;
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


