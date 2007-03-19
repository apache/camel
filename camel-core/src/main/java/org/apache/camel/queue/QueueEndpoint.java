/*
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
package org.apache.camel.queue;

import java.util.Queue;

import org.apache.camel.CamelContainer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;

/**
 * Represents a queue endpoint that uses a {@link Queue}
 * object to process inbound exchanges.
 *
 * @version $Revision: 519973 $
 */
public class QueueEndpoint<E> extends DefaultEndpoint<E> {
    private Queue<E> queue;

    public QueueEndpoint(String uri, CamelContainer container, Queue<E> queue) {
        super(uri, container);
        this.queue = queue;
    }

    public void send(E exchange) {
        queue.add(exchange);
    }

    public E createExchange() {
    	// How can we create a specific Exchange if we are generic??
    	// perhaps it would be better if we did not implement this. 
        return (E) new DefaultExchange();
    }

    public Queue<E> getQueue() {
        return queue;
    }
}
