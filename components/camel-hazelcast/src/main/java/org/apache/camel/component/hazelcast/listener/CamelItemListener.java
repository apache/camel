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
package org.apache.camel.component.hazelcast.listener;

import com.hazelcast.core.ItemEvent;
import com.hazelcast.core.ItemListener;

import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.HazelcastDefaultConsumer;

/**
 *
 */
public class CamelItemListener extends CamelListener implements ItemListener<Object> {

    public CamelItemListener(HazelcastDefaultConsumer consumer, String cacheName) {
        super(consumer, cacheName);
    }

    public void itemAdded(ItemEvent<Object> itemEvent) {
        this.sendExchange(HazelcastConstants.ADDED, null, itemEvent);
    }

    public void itemRemoved(ItemEvent<Object> itemEvent) {
        this.sendExchange(HazelcastConstants.REMOVED, null, itemEvent);
    }
}
