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

import static org.apache.camel.util.ObjectHelper.notNull;

import java.util.Iterator;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.converter.ObjectConverter;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.util.CollectionHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * Implements a dynamic <a
 * href="http://activemq.apache.org/camel/splitter.html">Splitter</a> pattern
 * where an expression is evaluated to iterate through each of the parts of a
 * message and then each part is then send to some endpoint.
 * 
 * @version $Revision$
 */
public class Splitter extends ServiceSupport implements Processor {
    public static final String SPLIT_SIZE = "org.apache.camel.splitSize";
    public static final String SPLIT_COUNTER = "org.apache.camel.splitCounter";

    private final Processor processor;
    private final Expression expression;
    private final AggregationStrategy aggregationStrategy;
    
    public Splitter(Expression expression, Processor destination, AggregationStrategy aggregationStrategy) {
        this.processor = destination;
        this.expression = expression;
        this.aggregationStrategy = aggregationStrategy;
        notNull(destination, "destination");
        notNull(expression, "expression");
        notNull(aggregationStrategy, "aggregationStrategy");
    }
    
    @Override
    public String toString() {
        return "Splitter[on: " + expression + " to: " + processor + " aggregate: " + aggregationStrategy + "]";
    }

    public void process(Exchange exchange) throws Exception {
        Object value = expression.evaluate(exchange);
        Integer size = CollectionHelper.size(value);
        Iterator iter = ObjectConverter.iterator(value);
        int counter = 0;
        Exchange result = null;
        while (iter.hasNext()) {
            Object part = iter.next();
            Exchange newExchange = exchange.copy();
            Message in = newExchange.getIn();
            in.setBody(part);
            if (size != null) {
                in.setHeader(SPLIT_SIZE, size);
            }
            in.setHeader(SPLIT_COUNTER, counter++);
            processor.process(newExchange);
            if (result == null) {
                result = newExchange;
            } else {
                result = aggregationStrategy.aggregate(result, newExchange);
            }
        }
        if (result != null) {
            ExchangeHelper.copyResults(exchange, result);
        }
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(processor);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(processor);
    }
}
