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

package org.apache.camel.test.infra.infinispan.common;

public final class InfinispanProperties {
    public static final String INFINISPAN_CONTAINER = "infinispan.container";
    public static final String SERVICE_ADDRESS = "infinispan.service.address";
    public static final String SERVICE_HOST = "infinispan.service.host";
    public static final String SERVICE_PORT = "infinispan.service.port";
    public static final String SERVICE_USERNAME = "infinispan.service.username";
    public static final String SERVICE_PASSWORD = "infinispan.service.password";
    public static final int DEFAULT_SERVICE_PORT = 11222;
    public static final String INFINISPAN_CONTAINER_NETWORK_MODE_HOST = "infinispan.service.network.mode.host";
    public static final boolean INFINISPAN_CONTAINER_NETWORK_MODE_HOST_DEFAULT = false;

    private InfinispanProperties() {

    }
}
