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
package org.apache.camel.component.stream;

import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class StreamEndpoint extends DefaultEndpoint<StreamExchange> {
    private static final Log LOG = LogFactory.getLog(StreamConsumer.class);
    Producer<StreamExchange> producer;
    private Map<String, String> parameters;
    private String uri;


    public StreamEndpoint(StreamComponent component, String uri, String remaining,
                          Map<String, String> parameters) throws Exception {
        super(uri, component);
        this.parameters = parameters;
        this.uri = uri;
        LOG.debug(uri + " / " + remaining + " / " + parameters);
        this.producer = new StreamProducer(this, uri, parameters);

    }

    public Consumer<StreamExchange> createConsumer(Processor p) throws Exception {
        return new StreamConsumer(this, p, uri, parameters);
    }

    public Producer<StreamExchange> createProducer() throws Exception {
        return producer;
    }

    public boolean isSingleton() {
        return true;
    }
}
