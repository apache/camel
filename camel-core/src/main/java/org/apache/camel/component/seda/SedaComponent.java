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
package org.apache.camel.component.seda;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultComponent;

/**
 * An implementation of the <a href="http://camel.apache.org/seda.html">SEDA components</a>
 * for asynchronous SEDA exchanges on a {@link BlockingQueue} within a CamelContext
 *
 * @version $Revision$
 */
public class SedaComponent extends DefaultComponent {

    private final Map<String, BlockingQueue<Exchange>> queues = new HashMap<String, BlockingQueue<Exchange>>();

    public synchronized BlockingQueue<Exchange> createQueue(String uri, Map parameters) {
        String key = getQueueKey(uri);

        if (queues.containsKey(key)) {
            return queues.get(key);
        }

        // create queue
        int size = getAndRemoveParameter(parameters, "size", Integer.class, 1000);
        BlockingQueue<Exchange> queue = new LinkedBlockingQueue<Exchange>(size);
        queues.put(key, queue);
        return queue;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        int consumers = getAndRemoveParameter(parameters, "concurrentConsumers", Integer.class, 1);
        SedaEndpoint answer = new SedaEndpoint(uri, this, createQueue(uri, parameters), consumers);
        answer.configureProperties(parameters);
        return answer;
    }

    protected String getQueueKey(String uri) {
        if (uri.contains("?")) {
            // strip parameters
            uri = uri.substring(0, uri.indexOf("?"));
        }
        return uri;
    }

    @Override
    protected void doStop() throws Exception {
        queues.clear();
        super.doStop();
    }

}
