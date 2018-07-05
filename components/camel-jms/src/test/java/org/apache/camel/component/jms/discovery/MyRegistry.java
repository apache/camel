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
package org.apache.camel.component.jms.discovery;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple POJO showing how to create a simple registry
 *
 * @version 
 */
public class MyRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(MyRegistry.class);

    private Map<String, Map<String, Object>> services = new HashMap<>();

    public void onEvent(Map<String, Object> heartbeat) {
        String key = (String) heartbeat.get("name");
        LOG.debug(">>> event for: " + key + " details: " + heartbeat);
        services.put(key, heartbeat);
    }

    public Map<String, Map<String, Object>> getServices() {
        return services;
    }
}
