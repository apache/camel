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
package org.apache.camel.component.consul;

import com.orbitz.consul.Consul;

public interface ConsulConstants {
    String CONSUL_DEFAULT_URL = String.format("http://%s:%d", Consul.DEFAULT_HTTP_HOST, Consul.DEFAULT_HTTP_PORT);

    // Service Call EIP
    String CONSUL_SERVER_IP = "CamelConsulServerIp";
    String CONSUL_SERVER_PORT = "CamelConsulServerPort";

    String CONSUL_ACTION = "CamelConsulAction";
    String CONSUL_KEY = "CamelConsulKey";
    String CONSUL_EVENT_ID = "CamelConsulEventId";
    String CONSUL_EVENT_NAME = "CamelConsulEventName";
    String CONSUL_EVENT_LTIME = "CamelConsulEventLTime";
    String CONSUL_NODE_FILTER = "CamelConsulNodeFilter";
    String CONSUL_TAG_FILTER = "CamelConsulTagFilter";
    String CONSUL_SERVICE_FILTER = "CamelConsulSessionFilter";
    String CONSUL_VERSION = "CamelConsulVersion";
    String CONSUL_FLAGS = "CamelConsulFlags";
    String CONSUL_INDEX = "CamelConsulIndex";
    String CONSUL_WAIT = "CamelConsulWait";
    String CONSUL_CREATE_INDEX = "CamelConsulCreateIndex";
    String CONSUL_LOCK_INDEX = "CamelConsulLockIndex";
    String CONSUL_MODIFY_INDEX = "CamelConsulModifyIndex";
    String CONSUL_OPTIONS = "CamelConsulOptions";
    String CONSUL_RESULT = "CamelConsulResult";
    String CONSUL_SESSION = "CamelConsulSession";
    String CONSUL_VALUE_AS_STRING = "CamelConsulValueAsString";
    String CONSUL_NODE = "CamelConsulNode";
    String CONSUL_SERVICE = "CamelConsulService";
    String CONSUL_DATACENTER = "CamelConsulDatacenter";
    String CONSUL_NEAR_NODE = "CamelConsulNearNode";
    String CONSUL_NODE_META = "CamelConsulNodeMeta";
    String CONSUL_LAST_CONTACT = "CamelConsulLastContact";
    String CONSUL_KNOWN_LEADER = "CamelConsulKnownLeader";
    String CONSUL_CONSISTENCY_MODE = "CamelConsulConsistencyMode";
    String CONSUL_HEALTHY_ONLY = "CamelConsulHealthyOnly";
    String CONSUL_HEALTHY_STATE = "CamelConsulHealthyState";
    String CONSUL_PREPARED_QUERY_ID = "CamelConsulPreparedQueryID";
}
