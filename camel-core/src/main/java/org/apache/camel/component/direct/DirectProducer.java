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
package org.apache.camel.component.direct;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The direct producer.
 *
 * @version $Revision$
 */
public class DirectProducer<E extends Exchange> extends DefaultProducer implements AsyncProcessor {
    private static final transient Log LOG = LogFactory.getLog(DirectProducer.class);
    private DirectEndpoint<E> endpoint;

    public DirectProducer(DirectEndpoint<E> endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        if (endpoint.getConsumers().isEmpty()) {
            LOG.warn("No getConsumers() available on " + this + " for " + exchange);
        } else {
            for (DefaultConsumer<E> consumer : endpoint.getConsumers()) {
                consumer.getProcessor().process(exchange);
            }
        }
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        int size = endpoint.getConsumers().size();
        if (size == 0) {
            LOG.warn("No getConsumers() available on " + this + " for " + exchange);
        } else if (size == 1) {
            DefaultConsumer<E> consumer = endpoint.getConsumers().get(0);
            AsyncProcessor processor = AsyncProcessorTypeConverter.convert(consumer.getProcessor());
            return processor.process(exchange, callback);
        } else if (size > 1) {
            // Too hard to do multiple async.. do it sync
            try {
                for (DefaultConsumer<E> consumer : endpoint.getConsumers()) {
                    consumer.getProcessor().process(exchange);
                }
            } catch (Throwable error) {
                exchange.setException(error);
            }
        }
        callback.done(true);
        return true;
    }
}
