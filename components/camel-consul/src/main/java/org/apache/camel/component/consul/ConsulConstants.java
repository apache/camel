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

    @Metadata(label = "producer", description = "The action to run, overriding the action option.", javaType = "String")
    String CONSUL_ACTION = "CamelConsulAction";
    @Metadata(description = "The consumer sets the key of the value (kv) or the event name (event). In a producer, the key (kv) or"
                            + " event name (event) to act on, overriding the key option.",
              javaType = "String")
    String CONSUL_KEY = "CamelConsulKey";
    // exchange property the key/value and event consumers set to the key they received, so that a producer can tell the
    // key header the consumer sets apart from a key chosen by the route
    String CONSUL_RECEIVED_KEY = "CamelConsulReceivedKey";
    @Metadata(label = "consumer", description = "The id of the received event.", javaType = "String")
    String CONSUL_EVENT_ID = "CamelConsulEventId";
    @Metadata(label = "consumer", description = "The name of the received event.", javaType = "String")
    String CONSUL_EVENT_NAME = "CamelConsulEventName";
    @Metadata(label = "consumer", description = "The Lamport time of the received event.", javaType = "Long")
    String CONSUL_EVENT_LTIME = "CamelConsulEventLTime";
    @Metadata(label = "consumer",
              description = "The node name filter (a regular expression) of the received event, when it has one.",
              javaType = "String")
    String CONSUL_NODE_FILTER = "CamelConsulNodeFilter";
    @Metadata(label = "consumer",
              description = "The service tag filter (a regular expression) of the received event, when it has one.",
              javaType = "String")
    String CONSUL_TAG_FILTER = "CamelConsulTagFilter";
    @Metadata(label = "consumer",
              description = "The service name filter (a regular expression) of the received event, when it has one.",
              javaType = "String")
    String CONSUL_SERVICE_FILTER = "CamelConsulSessionFilter";
    @Metadata(label = "consumer", description = "The version of the received event.", javaType = "Integer")
    String CONSUL_VERSION = "CamelConsulVersion";
    @Metadata(description = "The flags of the value. The kv consumer sets the flags of the value it received, and the PUT action"
                            + " stores them with the value (default 0).",
              javaType = "Long")
    String CONSUL_FLAGS = "CamelConsulFlags";
    @Metadata(label = "producer",
              description = "The index from which a blocking catalog or health query waits for a change. These actions set it"
                            + " to the index of the answer.",
              javaType = "BigInteger")
    String CONSUL_INDEX = "CamelConsulIndex";
    @Metadata(label = "producer",
              description = "The maximum wait time of a blocking catalog or health query, such as 10s or 5m.",
              javaType = "String")
    String CONSUL_WAIT = "CamelConsulWait";
    @Metadata(label = "consumer", description = "The index at which the key was created.",
              javaType = "Long")
    String CONSUL_CREATE_INDEX = "CamelConsulCreateIndex";
    @Metadata(label = "consumer", description = "The number of times that a lock on the key was acquired.",
              javaType = "Long")
    String CONSUL_LOCK_INDEX = "CamelConsulLockIndex";
    @Metadata(label = "consumer", description = "The index at which the key was last modified.", javaType = "Long")
    String CONSUL_MODIFY_INDEX = "CamelConsulModifyIndex";
    @Metadata(description = "The options of the request, as an object of org.kiwiproject.consul.option: PutOptions for PUT,"
                            + " QueryOptions for GET_VALUE, GET_VALUES and the event LIST, and EventOptions for FIRE.")
    String CONSUL_OPTIONS = "CamelConsulOptions";
    @Metadata(description = "Whether the action had a result: false when nothing was found, else the outcome of PUT, LOCK and"
                            + " UNLOCK, and true for the other actions. The consumers set it to true.",
              javaType = "Boolean")
    String CONSUL_RESULT = "CamelConsulResult";
    @Metadata(description = "The id of the session. The kv consumer sets the session that holds the lock on the key. LOCK and"
                            + " UNLOCK use it, and DESTROY, INFO and RENEW of session read it before the body.",
              javaType = "String")
    String CONSUL_SESSION = "CamelConsulSession";
    @Metadata(label = "producer",
              description = "Whether GET_VALUE and GET_VALUES return the values decoded to text, overriding the valueAsString"
                            + " option.",
              javaType = "Boolean")
    String CONSUL_VALUE_AS_STRING = "CamelConsulValueAsString";
    @Metadata(label = "producer",
              description = "The node name for the GET_NODE action of catalog and the NODE_CHECKS action of health.",
              javaType = "String")
    String CONSUL_NODE = "CamelConsulNode";
    @Metadata(label = "producer",
              description = "The service name for the GET_SERVICE action of catalog and the SERVICE_CHECKS and"
                            + " SERVICE_INSTANCES actions of health.",
              javaType = "String")
    String CONSUL_SERVICE = "CamelConsulService";
    @Metadata(label = "producer",
              description = "The datacenter to query, overriding the datacenter option. The session actions use only this"
                            + " header.",
              javaType = "String")
    String CONSUL_DATACENTER = "CamelConsulDatacenter";
    @Metadata(label = "producer",
              description = "The node from which Consul sorts the results of the catalog and health queries by round trip"
                            + " time, overriding the nearNode option.",
              javaType = "String")
    String CONSUL_NEAR_NODE = "CamelConsulNearNode";
    @Metadata(label = "producer",
              description = "The node metadata filters, each as key:value, of the catalog and health queries, overriding the"
                            + " nodeMeta option.",
              javaType = "List<String>")
    String CONSUL_NODE_META = "CamelConsulNodeMeta";
    @Metadata(label = "producer",
              description = "The time in milliseconds since the server that answered a catalog or health query last had contact"
                            + " with the leader.",
              javaType = "Long")
    String CONSUL_LAST_CONTACT = "CamelConsulLastContact";
    @Metadata(label = "producer", description = "Whether the server that answered a catalog or health query knew the leader.",
              javaType = "Boolean")
    String CONSUL_KNOWN_LEADER = "CamelConsulKnownLeader";
    @Metadata(label = "producer",
              description = "The consistency mode of the catalog and health queries, overriding the consistencyMode option.",
              javaType = "org.kiwiproject.consul.option.ConsistencyMode", defaultValue = "DEFAULT")
    String CONSUL_CONSISTENCY_MODE = "CamelConsulConsistencyMode";
    @Metadata(label = "producer",
              description = "Whether the SERVICE_INSTANCES action returns only the instances that pass all their health"
                            + " checks.",
              javaType = "Boolean", defaultValue = "false")
    String CONSUL_HEALTHY_ONLY = "CamelConsulHealthyOnly";
    @Metadata(label = "producer",
              description = "The state of the health checks that the CHECKS action of health returns. Required for that"
                            + " action.",
              javaType = "org.kiwiproject.consul.model.State")
    String CONSUL_HEALTHY_STATE = "CamelConsulHealthyState";
    @Metadata(label = "producer",
              description = "The id of the prepared query for GET and EXECUTE. When not set, the body holds the id.",
              javaType = "String")
    String CONSUL_PREPARED_QUERY_ID = "CamelConsulPreparedQueryID";
    @Metadata(label = "producer",
              description = "The id of the service that the DEREGISTER action of agent removes. Required for that action.",
              javaType = "String")
    String CONSUL_SERVICE_ID = "CamelConsulServiceId";
}
