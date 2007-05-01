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
package org.apache.camel.processor;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Service;
import org.apache.camel.impl.ServiceSupport;

/**
 * @version $Revision$
 */
public class SendProcessor extends ServiceSupport implements Processor, Service {
    private Endpoint destination;
    private Producer producer;

    public SendProcessor(Endpoint destination) {
        this.destination = destination;
    }

    protected void doStop() throws Exception {
        if (producer != null) {
            try {
                producer.stop();
            }
            finally {
                producer = null;
            }
        }
    }

    protected void doStart() throws Exception {
        this.producer = destination.createProducer();
    }

    public void process(Exchange exchange) throws Exception {
        if (producer == null) {
            throw new IllegalStateException("No producer, this processor has not been started!");
        }
        producer.process(exchange);
    }

    public Endpoint getDestination() {
        return destination;
    }

    @Override
    public String toString() {
        return "sendTo(" + destination + ")";
    }
}
