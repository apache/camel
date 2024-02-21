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
package org.apache.camel.impl.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

@DevConsole("blocked")
public class BlockedConsole extends AbstractDevConsole {

    public BlockedConsole() {
        super("camel", "blocked", "Blocked Exchanges", "Display blocked exchanges");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        AsyncProcessorAwaitManager am = PluginHelper.getAsyncProcessorAwaitManager(getCamelContext());
        sb.append(String.format("\n    Blocked: %s", am.size()));
        for (AsyncProcessorAwaitManager.AwaitThread at : am.browse()) {
            String age = TimeUtils.printDuration(at.getWaitDuration(), true);
            sb.append(String.format("\n    %s (at: %s/%s age: %s)",
                    at.getExchange().getExchangeId(), at.getRouteId(), at.getNodeId(), age));
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        AsyncProcessorAwaitManager am = PluginHelper.getAsyncProcessorAwaitManager(getCamelContext());
        root.put("blocked", am.size());

        final List<JsonObject> list = new ArrayList<>();
        for (AsyncProcessorAwaitManager.AwaitThread at : am.browse()) {
            JsonObject props = new JsonObject();
            props.put("exchangeId", at.getExchange().getExchangeId());
            props.put("routeId", at.getRouteId());
            props.put("nodeId", at.getNodeId());
            props.put("duration", at.getWaitDuration());
            list.add(props);
        }
        if (!list.isEmpty()) {
            root.put("exchanges", list);
        }
        return root;
    }

}
