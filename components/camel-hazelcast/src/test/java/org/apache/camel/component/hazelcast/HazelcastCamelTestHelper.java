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
package org.apache.camel.component.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.component.hazelcast.atomicnumber.HazelcastAtomicnumberComponent;
import org.apache.camel.component.hazelcast.instance.HazelcastInstanceComponent;
import org.apache.camel.component.hazelcast.list.HazelcastListComponent;
import org.apache.camel.component.hazelcast.map.HazelcastMapComponent;
import org.apache.camel.component.hazelcast.multimap.HazelcastMultimapComponent;
import org.apache.camel.component.hazelcast.queue.HazelcastQueueComponent;
import org.apache.camel.component.hazelcast.replicatedmap.HazelcastReplicatedmapComponent;
import org.apache.camel.component.hazelcast.ringbuffer.HazelcastRingbufferComponent;
import org.apache.camel.component.hazelcast.seda.HazelcastSedaComponent;
import org.apache.camel.component.hazelcast.set.HazelcastSetComponent;
import org.apache.camel.component.hazelcast.topic.HazelcastTopicComponent;

public final class HazelcastCamelTestHelper {

    private HazelcastCamelTestHelper() {
    }

    public static void registerHazelcastComponents(CamelContext context, HazelcastInstance hazelcastInstance) {
        HazelcastAtomicnumberComponent atomic = new HazelcastAtomicnumberComponent(context);
        atomic.setHazelcastInstance(hazelcastInstance);
        context.addComponent("hazelcast-atomicvalue", atomic);
        HazelcastInstanceComponent instance = new HazelcastInstanceComponent(context);
        instance.setHazelcastInstance(hazelcastInstance);
        context.addComponent("hazelcast-instance", instance);
        HazelcastListComponent list = new HazelcastListComponent(context);
        list.setHazelcastInstance(hazelcastInstance);
        context.addComponent("hazelcast-list", list);
        HazelcastMapComponent map = new HazelcastMapComponent(context);
        map.setHazelcastInstance(hazelcastInstance);
        context.addComponent("hazelcast-map", map);
        HazelcastMultimapComponent multimap = new HazelcastMultimapComponent(context);
        multimap.setHazelcastInstance(hazelcastInstance);
        context.addComponent("hazelcast-multimap", multimap);
        HazelcastQueueComponent queue = new HazelcastQueueComponent(context);
        queue.setHazelcastInstance(hazelcastInstance);
        context.addComponent("hazelcast-queue", queue);
        HazelcastReplicatedmapComponent replicatedmap = new HazelcastReplicatedmapComponent(context);
        replicatedmap.setHazelcastInstance(hazelcastInstance);
        context.addComponent("hazelcast-replicatedmap", replicatedmap);
        HazelcastRingbufferComponent ringbuffer = new HazelcastRingbufferComponent(context);
        ringbuffer.setHazelcastInstance(hazelcastInstance);
        context.addComponent("hazelcast-ringbuffer", ringbuffer);
        HazelcastSedaComponent seda = new HazelcastSedaComponent(context);
        seda.setHazelcastInstance(hazelcastInstance);
        context.addComponent("hazelcast-seda", seda);
        HazelcastSetComponent set = new HazelcastSetComponent(context);
        set.setHazelcastInstance(hazelcastInstance);
        context.addComponent("hazelcast-set", set);
        HazelcastTopicComponent topic = new HazelcastTopicComponent(context);
        topic.setHazelcastInstance(hazelcastInstance);
        context.addComponent("hazelcast-topic", topic);
    }

}
