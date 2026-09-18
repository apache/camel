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

import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "inflight", displayName = "Inflight Exchanges", description = "Display inflight exchanges")
public class InflightConsole extends AbstractDevConsole {

    @Metadata(label = "query", description = "Filters the routes matching by route id, route uri",
              javaType = "java.lang.String")
    public static final String FILTER = "filter";

    @Metadata(label = "query", description = "Limits the number of entries displayed", javaType = "java.lang.Integer")
    public static final String LIMIT = "limit";

    public record Exchange(
            @Metadata(description = "The exchange ID") String exchangeId,
            @Metadata(description = "The route ID where the exchange originated from") String fromRouteId,
            @Metadata(description = "Whether the exchange originated from a remote endpoint") boolean fromRemoteEndpoint,
            @Metadata(description = "The route ID where the exchange currently is") String atRouteId,
            @Metadata(description = "The node ID where the exchange currently is") String nodeId,
            @Metadata(description = "Elapsed time in milliseconds since the exchange started") long elapsed,
            @Metadata(description = "Duration in milliseconds the exchange has been at the current node") long duration) {
    }

    public record Response(
            @Metadata(description = "Number of inflight exchanges") int inflight,
            @Metadata(description = "Whether browsing inflight exchanges is enabled") boolean inflightBrowseEnabled,
            @Metadata(description = "The inflight exchanges (only present when browsing is enabled)") List<Exchange> exchanges) {
    }

    public InflightConsole() {
        super("camel", "inflight", "Inflight Exchanges", "Display inflight exchanges");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String filter = optionString(options, FILTER);
        int max = optionInt(options, LIMIT, Integer.MAX_VALUE);

        StringBuilder sb = new StringBuilder();

        InflightRepository repo = getCamelContext().getInflightRepository();
        sb.append(String.format("%n    Inflight: %s", repo.size()));
        sb.append(String.format("%n    InflightBrowseEnabled: %s", repo.isInflightBrowseEnabled()));
        if (repo.isInflightBrowseEnabled()) {
            for (InflightRepository.InflightExchange ie : repo.browse(filter, max, false)) {
                String age = TimeUtils.printDuration(ie.getDuration(), true);
                sb.append(String.format("%n    %s (from: %s at: %s/%s remote: %b age: %s)",
                        ie.getExchange().getExchangeId(), ie.getFromRouteId(), ie.getAtRouteId(), ie.getNodeId(),
                        ie.isFromRemoteEndpoint(), age));
            }
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String filter = optionString(options, FILTER);
        int max = optionInt(options, LIMIT, Integer.MAX_VALUE);

        InflightRepository repo = getCamelContext().getInflightRepository();
        boolean browseEnabled = repo.isInflightBrowseEnabled();

        List<Exchange> exchanges = null;
        if (browseEnabled) {
            exchanges = new ArrayList<>();
            for (InflightRepository.InflightExchange ie : repo.browse(filter, max, false)) {
                exchanges.add(new Exchange(
                        ie.getExchange().getExchangeId(), ie.getFromRouteId(), ie.isFromRemoteEndpoint(),
                        ie.getAtRouteId(), ie.getNodeId(), ie.getElapsed(), ie.getDuration()));
            }
        }

        Response response = new Response(repo.size(), browseEnabled, exchanges);
        return JsonRecordSupport.toJsonObject(response);
    }
}
