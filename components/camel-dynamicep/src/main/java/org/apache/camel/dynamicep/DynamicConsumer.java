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
package org.apache.camel.dynamicep;


import java.net.URLDecoder;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;

/**
 * Parses the endpoint uri and creates one consumer for each
 * endpopint to listen on
 */
public class DynamicConsumer extends DefaultConsumer {
    Set<Consumer> consumers;

    public DynamicConsumer(DynamicEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        consumers = new HashSet<Consumer>();
        String uris = endpoint.getEndpointUri().substring("dynamicep://".length());
        String[] uriAr = uris.split(",|%2C");
        for (String epUri : uriAr) {
            try {
                epUri = URLDecoder.decode(epUri, "UTF-8");
                Endpoint ep = this.getEndpoint().getCamelContext().getEndpoint(epUri);            
                Consumer consumer = ep.createConsumer(getProcessor());
                consumers.add(consumer);
            } catch (Exception e) {
                throw new RuntimeException("Error initializing endpoint dynamic endpoint from " + uris  + " at " + epUri, e);
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        for (Consumer consumer : consumers) {
            consumer.start();
        }
    }

    @Override
    protected void doStop() throws Exception {
        for (Consumer consumer : consumers) {
            consumer.stop();
        }
        super.doStop();
    }
    
}
