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
package org.apache.camel.component.consul;

import org.apache.camel.spi.Metadata;
import org.kiwiproject.consul.Consul;

public interface ConsulConstants {
    String CONSUL_DEFAULT_URL = String.format("http://%s:%d", Consul.DEFAULT_HTTP_HOST, Consul.DEFAULT_HTTP_PORT);

    // Service Call EIP
    String CONSUL_SERVER_IP = "CamelConsulServerIp";
    String CONSUL_SERVER_PORT = "CamelConsulServerPort";

    @Metadata(label = "producer", description = "The Producer action", javaType = "String")
    String CONSUL_ACTION = "CamelConsulAction";
    @Metadata(description = "The Key on which the action should applied", javaType = "String")
    String CONSUL_KEY = "CamelConsulKey";
    @Metadata(label = "consumer", description = "The event id", javaType = "String")
    String CONSUL_EVENT_ID = "CamelConsulEventId";
    @Metadata(label = "consumer", description = "The event name", javaType = "String")
    String CONSUL_EVENT_NAME = "CamelConsulEventName";
    @Metadata(label = "consumer", description = "The event LTime", javaType = "Long")
    String CONSUL_EVENT_LTIME = "CamelConsulEventLTime";
    @Metadata(label = "consumer", description = "The Node filter", javaType = "String")
    String CONSUL_NODE_FILTER = "CamelConsulNodeFilter";
    @Metadata(label = "consumer", description = "The tag filter", javaType = "String")
    String CONSUL_TAG_FILTER = "CamelConsulTagFilter";
    @Metadata(label = "consumer", description = "The session filter", javaType = "String")
    String CONSUL_SERVICE_FILTER = "CamelConsulSessionFilter";
    @Metadata(label = "consumer", description = "The data version", javaType = "Integer")
    String CONSUL_VERSION = "CamelConsulVersion";
    @Metadata(description = "Flags associated with a value", javaType = "Long")
    String CONSUL_FLAGS = "CamelConsulFlags";
    @Metadata(label = "producer", description = "The optional value index", javaType = "BigInteger")
    String CONSUL_INDEX = "CamelConsulIndex";
    @Metadata(label = "producer", description = "The optional value wait", javaType = "String")
    String CONSUL_WAIT = "CamelConsulWait";
    @Metadata(label = "consumer", description = "The internal index value that represents when the entry was created",
              javaType = "Long")
    String CONSUL_CREATE_INDEX = "CamelConsulCreateIndex";
    @Metadata(label = "consumer", description = "The number of times this key has successfully been acquired in a lock",
              javaType = "Long")
    String CONSUL_LOCK_INDEX = "CamelConsulLockIndex";
    @Metadata(label = "consumer", description = "The last index that modified this key", javaType = "Long")
    String CONSUL_MODIFY_INDEX = "CamelConsulModifyIndex";
    @Metadata(description = "Options associated to the request")
    String CONSUL_OPTIONS = "CamelConsulOptions";
    @Metadata(description = "true if the response has a result", javaType = "Boolean")
    String CONSUL_RESULT = "CamelConsulResult";
    @Metadata(description = "The session id", javaType = "String")
    String CONSUL_SESSION = "CamelConsulSession";
    @Metadata(label = "producer", description = "To transform values retrieved from Consul i.e. on KV endpoint to string.",
              javaType = "Boolean")
    String CONSUL_VALUE_AS_STRING = "CamelConsulValueAsString";
    @Metadata(label = "producer", description = "The node", javaType = "String")
    String CONSUL_NODE = "CamelConsulNode";
    @Metadata(label = "producer", description = "The service", javaType = "String")
    String CONSUL_SERVICE = "CamelConsulService";
    @Metadata(label = "producer", description = "The data center", javaType = "String")
    String CONSUL_DATACENTER = "CamelConsulDatacenter";
    @Metadata(label = "producer", description = "The near node to use for queries.", javaType = "String")
    String CONSUL_NEAR_NODE = "CamelConsulNearNode";
    @Metadata(label = "producer", description = "The note meta-data to use for queries.", javaType = "List<String>")
    String CONSUL_NODE_META = "CamelConsulNodeMeta";
    @Metadata(label = "producer", description = "The last contact", javaType = "Long")
    String CONSUL_LAST_CONTACT = "CamelConsulLastContact";
    @Metadata(label = "producer", description = "Indicates whether it is the known leader", javaType = "Boolean")
    String CONSUL_KNOWN_LEADER = "CamelConsulKnownLeader";
    @Metadata(label = "producer", description = "The consistencyMode used for queries",
              javaType = "org.kiwiproject.consul.option.ConsistencyMode", defaultValue = "DEFAULT")
    String CONSUL_CONSISTENCY_MODE = "CamelConsulConsistencyMode";
    @Metadata(label = "producer", description = "Only on healthy services", javaType = "Boolean", defaultValue = "false")
    String CONSUL_HEALTHY_ONLY = "CamelConsulHealthyOnly";
    @Metadata(label = "producer", description = "The state to query.", javaType = "org.kiwiproject.consul.model.State")
    String CONSUL_HEALTHY_STATE = "CamelConsulHealthyState";
    @Metadata(label = "producer", description = "The id of the prepared query", javaType = "String")
    String CONSUL_PREPARED_QUERY_ID = "CamelConsulPreparedQueryID";
    @Metadata(label = "producer", description = "The service id for agent deregistration", javaType = "String")
    String CONSUL_SERVICE_ID = "CamelConsulServiceId";
}
