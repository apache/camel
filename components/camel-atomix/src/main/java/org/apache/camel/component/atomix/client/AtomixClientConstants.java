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
package org.apache.camel.component.atomix.client;

import org.apache.camel.spi.Metadata;

public final class AtomixClientConstants {

    // SCHEMES
    public static final String SCHEME_MAP = "atomix-map";
    public static final String SCHEME_MESSAGING = "atomix-messaging";
    public static final String SCHEME_MULTIMAP = "atomix-multimap";
    public static final String SCHEME_QUEUE = "atomix-queue";
    public static final String SCHEME_SET = "atomix-set";
    public static final String SCHEME_VALUE = "atomix-value";

    @Metadata(label = "producer", description = "The name of the resource", javaType = "String")
    public static final String RESOURCE_NAME = "CamelAtomixResourceName";
    public static final String RESOURCE_ACTION = "CamelAtomixResourceAction";
    @Metadata(label = "producer", description = "The action to perform",
              javaType = "org.apache.camel.component.atomix.client.map.AtomixMap.Action", applicableFor = SCHEME_MAP)
    public static final String RESOURCE_ACTION_MAP = RESOURCE_ACTION;
    @Metadata(label = "producer", description = "The action to perform",
              javaType = "org.apache.camel.component.atomix.client.messaging.AtomixMessaging.Action",
              applicableFor = SCHEME_MESSAGING)
    public static final String RESOURCE_ACTION_MESSAGING = RESOURCE_ACTION;
    @Metadata(label = "producer", description = "The action to perform",
              javaType = "org.apache.camel.component.atomix.client.multimap.AtomixMultiMap.Action",
              applicableFor = SCHEME_MULTIMAP)
    public static final String RESOURCE_ACTION_MULTIMAP = RESOURCE_ACTION;
    @Metadata(label = "producer", description = "The action to perform",
              javaType = "org.apache.camel.component.atomix.client.queue.AtomixQueue.Action", applicableFor = SCHEME_QUEUE)
    public static final String RESOURCE_ACTION_QUEUE = RESOURCE_ACTION;
    @Metadata(label = "producer", description = "The action to perform",
              javaType = "org.apache.camel.component.atomix.client.set.AtomixSet.Action", applicableFor = SCHEME_SET)
    public static final String RESOURCE_ACTION_SET = RESOURCE_ACTION;
    @Metadata(label = "producer", description = "The action to perform",
              javaType = "org.apache.camel.component.atomix.client.value.AtomixValue.Action", applicableFor = SCHEME_VALUE)
    public static final String RESOURCE_ACTION_VALUE = RESOURCE_ACTION;
    @Metadata(description = "The key to operate on", javaType = "Object", applicableFor = { SCHEME_MAP, SCHEME_MULTIMAP })
    public static final String RESOURCE_KEY = "CamelAtomixResourceKey";
    @Metadata(label = "producer", description = "The value, if missing In Body is used", javaType = "Object")
    public static final String RESOURCE_VALUE = "CamelAtomixResourceValue";
    @Metadata(label = "producer", description = "The default value of the resource", javaType = "Object",
              applicableFor = SCHEME_MAP)
    public static final String RESOURCE_DEFAULT_VALUE = "CamelAtomixResourceDefaultValue";
    @Metadata(description = "The old value", javaType = "Object", applicableFor = { SCHEME_MAP, SCHEME_VALUE })
    public static final String RESOURCE_OLD_VALUE = "CamelAtomixResourceOldValue";
    @Metadata(label = "producer", description = "Flag indicating that the resource action has result to provide in the message",
              javaType = "boolean")
    public static final String RESOURCE_ACTION_HAS_RESULT = "CamelAtomixResourceActionHasResult";
    @Metadata(label = "producer", description = "The time to live of the entry", javaType = "String / long",
              applicableFor = { SCHEME_MAP, SCHEME_MULTIMAP, SCHEME_SET, SCHEME_VALUE })
    public static final String RESOURCE_TTL = "CamelAtomixResourceTTL";
    @Metadata(label = "producer", description = "The read consistency level", javaType = "io.atomix.resource.ReadConsistency",
              applicableFor = { SCHEME_MAP, SCHEME_MULTIMAP, SCHEME_QUEUE, SCHEME_SET, SCHEME_VALUE })
    public static final String RESOURCE_READ_CONSISTENCY = "CamelAtomixResourceReadConsistency";
    @Metadata(label = "consumer", description = "The type of event received",
              javaType = "io.atomix.resource.Resource.EventType",
              applicableFor = { SCHEME_MAP, SCHEME_QUEUE, SCHEME_SET, SCHEME_VALUE })
    public static final String EVENT_TYPE = "CamelAtomixEventType";
    @Metadata(label = "consumer", description = "The id of the message", javaType = "long", applicableFor = SCHEME_MESSAGING)
    public static final String MESSAGE_ID = "CamelAtomixEventType";
    @Metadata(label = "producer", description = "The Atomix Group member name", javaType = "String",
              applicableFor = SCHEME_MESSAGING)
    public static final String MEMBER_NAME = "CamelAtomixMemberName";
    @Metadata(label = "producer", description = "The messaging channel name", javaType = "String",
              applicableFor = SCHEME_MESSAGING)
    public static final String CHANNEL_NAME = "CamelAtomixChannelName";
    @Metadata(label = "producer", description = "The broadcast type",
              javaType = "org.apache.camel.component.atomix.client.messaging.AtomixMessaging.BroadcastType",
              applicableFor = SCHEME_MESSAGING)
    public static final String BROADCAST_TYPE = "CamelAtomixBroadcastType";

    private AtomixClientConstants() {
    }

}
