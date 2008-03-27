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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision: 1.1 $
 */
public class DataSetConsumer extends DefaultConsumer<Exchange> {
    private static final transient Log LOG = LogFactory.getLog(DataSetConsumer.class);
    private DataSetEndpoint endpoint;

    public DataSetConsumer(DataSetEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        endpoint.getExecutorService().execute(new Runnable() {
            public void run() {
                sendMessages();
            }
        });
    }

    protected void sendMessages() {
        try {
            DataSet dataSet = endpoint.getDataSet();
            for (long i = 0; i < dataSet.getSize(); i++) {
                Exchange exchange = endpoint.createExchange(i);
                getProcessor().process(exchange);

                long delay = endpoint.getProduceDelay();
                if (delay > 0) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        LOG.debug(e);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error(e);
        }
    }
}
