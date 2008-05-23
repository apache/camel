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



import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.converter.ObjectConverter;
import org.apache.camel.impl.ProducerCache;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.util.ExchangeHelper;
import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Implements a dynamic <a
 * href="http://activemq.apache.org/camel/recipient-list.html">Recipient List</a>
 * pattern where the list of actual endpoints to send a message exchange to are
 * dependent on some dynamic expression.
 *
 * @version $Revision$
 */
public class RecipientList extends ServiceSupport implements Processor {
    private final Expression<Exchange> expression;
    private ProducerCache<Exchange> producerCache = new ProducerCache<Exchange>();

    public RecipientList(Expression<Exchange> expression) {
        notNull(expression, "expression");
        this.expression = expression;
    }

    @Override
    public String toString() {
        return "RecipientList[" + expression + "]";
    }

    public void process(Exchange exchange) throws Exception {
        Object receipientList = expression.evaluate(exchange);
        Iterator iter = ObjectConverter.iterator(receipientList);
        List<Processor> processors = new ArrayList<Processor>();
        while (iter.hasNext()) {
            Object recipient = iter.next();
            Endpoint<Exchange> endpoint = resolveEndpoint(exchange, recipient);
            Producer<Exchange> producer = producerCache.getProducer(endpoint);
            processors.add(producer);
        }
        MulticastProcessor mp = new MulticastProcessor(processors, new UseLatestAggregationStrategy());
        mp.process(exchange);
    }

    protected Endpoint<Exchange> resolveEndpoint(Exchange exchange, Object recipient) {
        return ExchangeHelper.resolveEndpoint(exchange, recipient);
    }

    protected void doStop() throws Exception {
        producerCache.stop();
    }

    protected void doStart() throws Exception {
    }
}
