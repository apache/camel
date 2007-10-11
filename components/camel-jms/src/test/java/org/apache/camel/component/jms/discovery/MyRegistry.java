/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jms.discovery;

import java.util.Map;
import java.util.HashMap;

/**
 * A simple POJO showing how to create a simple registry
 * 
 * @version $Revision: 1.1 $
 */
public class MyRegistry {

    private Map<String,Map> services = new HashMap<String, Map>();

    public void onEvent(Map heartbeat) {
        String key = (String) heartbeat.get("name");
        services.put(key, heartbeat);
    }

    public Map<String, Map> getServices() {
        return services;
    }
}
