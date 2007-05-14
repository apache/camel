/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.util;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.ServiceSupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @version $Revision$
 */
public class ProducerCache<E extends Exchange> extends ServiceSupport {
    private static final Log log = LogFactory.getLog(ProducerCache.class);

    private Map<String, Producer<E>> producers = new HashMap<String, Producer<E>>();

    public synchronized Producer<E> getProducer(Endpoint<E> endpoint) {
        String key = endpoint.getEndpointUri();
        Producer<E> answer = producers.get(key);
        if (answer == null) {
            try {
                answer = endpoint.createProducer();
                answer.start();
            }
            catch (Exception e) {
                throw new FailedToCreateProducerException(endpoint, e);
            }
            producers.put(key, answer);
        }
        return answer;
    }

    /**
     * Sends the exchange to the given endpoint
     *
     * @param endpoint the endpoint to send the exchange to
     * @param exchange the exchange to send
     */
    public void send(Endpoint<E> endpoint, E exchange) {
        try {
            Producer<E> producer = getProducer(endpoint);
            producer.process(exchange);
        }
        catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * Sends an exchange to an endpoint using a supplied @{link Processor} to populate the exchange
     *
     * @param endpoint  the endpoint to send the exchange to
     * @param processor the transformer used to populate the new exchange
     */
    public E send(Endpoint<E> endpoint, Processor processor) {
        try {
            Producer<E> producer = getProducer(endpoint);
            E exchange = producer.createExchange();

            // lets populate using the processor callback
            processor.process(exchange);

            // now lets dispatch
            if (log.isDebugEnabled()) {
                log.debug(">>>> " + endpoint + " " + exchange);
            }
            producer.process(exchange);
            return exchange;
        }
        catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(producers.values());
    }

    protected void doStart() throws Exception {
    }
}
