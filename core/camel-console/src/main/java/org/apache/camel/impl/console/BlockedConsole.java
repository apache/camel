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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "blocked", displayName = "Blocked Exchanges", description = "Display blocked exchanges")
public class BlockedConsole extends AbstractDevConsole {

    public record Entry(
            @Metadata(description = "The exchange ID") String exchangeId,
            @Metadata(description = "The route ID") String routeId,
            @Metadata(description = "The node ID") String nodeId,
            @Metadata(description = "The wait duration in milliseconds") long duration) {
    }

    public record Response(
            @Metadata(description = "Number of blocked exchanges") int blocked,
            @Metadata(description = "The blocked exchanges (only present when there are any)") List<Entry> exchanges) {
    }

    public BlockedConsole() {
        super("camel", "blocked", "Blocked Exchanges", "Display blocked exchanges");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        AsyncProcessorAwaitManager am = PluginHelper.getAsyncProcessorAwaitManager(getCamelContext());
        sb.append(String.format("%n    Blocked: %s", am.size()));
        for (AsyncProcessorAwaitManager.AwaitThread at : am.browse()) {
            String age = TimeUtils.printDuration(at.getWaitDuration(), true);
            sb.append(String.format("%n    %s (at: %s/%s age: %s)",
                    at.getExchange().getExchangeId(), at.getRouteId(), at.getNodeId(), age));
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        AsyncProcessorAwaitManager am = PluginHelper.getAsyncProcessorAwaitManager(getCamelContext());

        List<Entry> entries = new ArrayList<>();
        for (AsyncProcessorAwaitManager.AwaitThread at : am.browse()) {
            entries.add(new Entry(
                    at.getExchange().getExchangeId(), at.getRouteId(), at.getNodeId(), at.getWaitDuration()));
        }

        Response response = new Response(am.size(), entries.isEmpty() ? null : entries);
        return JsonRecordSupport.toJsonObject(response);
    }

}
