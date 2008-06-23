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
package org.apache.camel.component.dataset;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.processor.ThroughputLogger;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * DataSet consumer.
 *
 * @version $Revision$
 */
public class DataSetConsumer extends DefaultConsumer<Exchange> {
    private static final transient Log LOG = LogFactory.getLog(DataSetConsumer.class);
    private DataSetEndpoint endpoint;
    private Processor reporter;

    public DataSetConsumer(DataSetEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (reporter == null) {
            reporter = createReporter();
        }
        final DataSet dataSet = endpoint.getDataSet();
        final long preloadSize = endpoint.getPreloadSize();

        sendMessages(0, preloadSize);

        endpoint.getExecutorService().execute(new Runnable() {
            public void run() {
                sendMessages(preloadSize, dataSet.getSize());
            }
        });
    }

    protected void sendMessages(long startIndex, long endIndex) {
        try {
            for (long i = startIndex; i < endIndex; i++) {
                Exchange exchange = endpoint.createExchange(i);
                getProcessor().process(exchange);

                try {
                    long delay = endpoint.getProduceDelay();
                    if (delay < 3) {
                        // if no delay set then we must sleep at lest for 3 millis to avoid concurrency
                        // issues with extremely high throughput
                        delay = 3;
                    }
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    // ignore and just log to debug
                    LOG.debug(e);
                }
                if (reporter != null) {
                    reporter.process(exchange);
                }
            }
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    protected ThroughputLogger createReporter() {
        ThroughputLogger answer = new ThroughputLogger(endpoint.getEndpointUri(), (int) endpoint.getDataSet().getReportCount());
        answer.setAction("Sent");
        return answer;
    }
}
