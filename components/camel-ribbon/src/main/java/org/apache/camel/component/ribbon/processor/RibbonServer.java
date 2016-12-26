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
package org.apache.camel.component.ribbon.processor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.netflix.loadbalancer.Server;
import org.apache.camel.spi.ServiceCallServer;

import static org.apache.camel.util.ObjectHelper.ifNotEmpty;

public class RibbonServer extends Server implements ServiceCallServer {

    public RibbonServer(String host, int port) {
        super(host, port);
    }

    @Override
    public String getIp() {
        return super.getHost();
    }

    @Override
    public int getPort() {
        return super.getPort();
    }

    @Override
    public Map<String, String> getMetadata() {
        Map<String, String> meta = new HashMap<>();
        ifNotEmpty(super.getId(), val -> meta.put("id", val));
        ifNotEmpty(super.getZone(), val -> meta.put("zone", val));

        if (super.getMetaInfo() != null) {
            ifNotEmpty(super.getMetaInfo().getAppName(), val -> meta.put("app_name", val));
            ifNotEmpty(super.getMetaInfo().getServiceIdForDiscovery(),  val -> meta.put("discovery_id", val));
            ifNotEmpty(super.getMetaInfo().getInstanceId(),  val -> meta.put("instance_id", val));
            ifNotEmpty(super.getMetaInfo().getServerGroup(), val -> meta.put("server_group", val));
        }

        return Collections.unmodifiableMap(meta);
    }
}
