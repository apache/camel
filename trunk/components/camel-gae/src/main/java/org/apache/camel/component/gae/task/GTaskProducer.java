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
package org.apache.camel.component.gae.task;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.TaskOptions;

import org.apache.camel.Exchange;
import org.apache.camel.component.gae.bind.OutboundBinding;
import org.apache.camel.impl.DefaultProducer;

public class GTaskProducer extends DefaultProducer {

    public GTaskProducer(GTaskEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public GTaskEndpoint getEndpoint() {
        return (GTaskEndpoint)super.getEndpoint();
    }
    
    public OutboundBinding<GTaskEndpoint, TaskOptions, Void> getOutboundBinding() {
        return getEndpoint().getOutboundBinding();
    }
    
    public Queue getQueue() {
        return getEndpoint().getQueue();
    }
    
    /**
     * Adds a the <code>exchange</code>'s in-message data to a task queue.
     * 
     * @see GTaskBinding
     */
    public void process(Exchange exchange) throws Exception {
        getQueue().add(getOutboundBinding().writeRequest(getEndpoint(), exchange, null));
    }

}
