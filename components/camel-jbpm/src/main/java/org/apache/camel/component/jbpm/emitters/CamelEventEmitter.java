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
package org.apache.camel.component.jbpm.emitters;

import java.util.Collection;

import org.apache.camel.component.jbpm.JBPMConsumer;
import org.jbpm.persistence.api.integration.EventCollection;
import org.jbpm.persistence.api.integration.EventEmitter;
import org.jbpm.persistence.api.integration.InstanceView;
import org.jbpm.persistence.api.integration.base.BaseEventCollection;

public class CamelEventEmitter implements EventEmitter {
    
    private JBPMConsumer consumer;
    private boolean sendItems;
    
    public CamelEventEmitter(JBPMConsumer consumer, boolean sendItems) {
        this.consumer = consumer; 
        this.sendItems = sendItems;
    }

    @Override
    public void deliver(Collection<InstanceView<?>> data) {
        // no-op
        
    }

    @Override
    public void apply(Collection<InstanceView<?>> data) {
        if (consumer == null || data.isEmpty()) {
            return;
        }
        
        if (sendItems) {
            
            data.forEach(item -> consumer.sendMessage("Emitter", item));
        } else {
        
            consumer.sendMessage("Emitter", data);
        }
    }

    @Override
    public void drop(Collection<InstanceView<?>> data) {
        // no-op
        
    }

    @Override
    public EventCollection newCollection() {
        return new BaseEventCollection();
    }

    @Override
    public void close() {

    }

}
