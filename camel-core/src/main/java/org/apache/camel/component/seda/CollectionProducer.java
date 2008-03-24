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

import java.util.Collection;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultProducer;

/**
 * A simple {@link Producer} which just appends to a {@link Collection} the {@link Exchange} object.
 *
 * @version $Revision$
 */
public class CollectionProducer extends DefaultProducer implements AsyncProcessor {
    private final Collection<Exchange> queue;

    public CollectionProducer(Endpoint endpoint, Collection<Exchange> queue) {
        super(endpoint);
        this.queue = queue;
    }

    public void process(Exchange exchange) throws Exception {
        queue.add(exchange.copy());
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        queue.add(exchange.copy());
        callback.done(true);
        return true;
    }
}
