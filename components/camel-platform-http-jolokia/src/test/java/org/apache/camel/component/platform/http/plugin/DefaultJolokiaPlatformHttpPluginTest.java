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
package org.apache.camel.component.platform.http.plugin;

import java.util.HashMap;
import java.util.Optional;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.platform.http.spi.PlatformHttpPluginRegistry;
import org.apache.camel.support.ResolverHelper;
import org.jolokia.server.core.http.HttpRequestHandler;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultJolokiaPlatformHttpPluginTest extends ContextTestSupport {

    @Test
    public void jolokiaPlatformHttpPluginContextTest() throws Exception {
        PlatformHttpPluginRegistry registry = resolvePlatformHttpPluginRegistry();
        Assertions.assertNotNull(registry);
        DefaultJolokiaPlatformHttpPlugin plugin = registry
                .resolvePluginById("jolokia", DefaultJolokiaPlatformHttpPlugin.class).orElseThrow();
        Assertions.assertNotNull(plugin);
        HttpRequestHandler handler = plugin.getJolokiaRequestHandler();

        JSONAware json = handler.handleGetRequest("", "/", new HashMap<>());

        JSONParser parser = new JSONParser();
        JSONObject responseBody = (JSONObject) parser.parse(json.toJSONString());

        JSONObject value = (JSONObject) responseBody.get("value");
        String agentVersion = (String) value.get("agent");

        JSONObject request = (JSONObject) responseBody.get("request");
        String type = (String) request.get("type");

        assertEquals("version", type);
        assertEquals("2.0.1", agentVersion);
    }

    private PlatformHttpPluginRegistry resolvePlatformHttpPluginRegistry() {
        Optional<PlatformHttpPluginRegistry> result = ResolverHelper.resolveService(
                context,
                PlatformHttpPluginRegistry.FACTORY,
                PlatformHttpPluginRegistry.class);
        return result.orElse(null);
    }
}
