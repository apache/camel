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
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

@DevConsole("inflight")
public class InflightConsole extends AbstractDevConsole {

    /**
     * Filters the routes matching by route id, route uri
     */
    public static final String FILTER = "filter";

    /**
     * Limits the number of entries displayed
     */
    public static final String LIMIT = "limit";

    public InflightConsole() {
        super("camel", "inflight", "Inflight Exchanges", "Display inflight exchanges");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String filter = (String) options.get(FILTER);
        String limit = (String) options.get(LIMIT);
        int max = limit == null ? Integer.MAX_VALUE : Integer.parseInt(limit);

        StringBuilder sb = new StringBuilder();

        InflightRepository repo = getCamelContext().getInflightRepository();
        sb.append(String.format("\n    Inflight: %s", repo.size()));
        sb.append(String.format("\n    InflightBrowseEnabled: %s", repo.isInflightBrowseEnabled()));
        if (repo.isInflightBrowseEnabled()) {
            for (InflightRepository.InflightExchange ie : repo.browse(filter, max, false)) {
                String age = TimeUtils.printDuration(ie.getDuration(), true);
                sb.append(String.format("\n    %s (from: %s at: %s/%s age: %s)",
                        ie.getExchange().getExchangeId(), ie.getFromRouteId(), ie.getAtRouteId(), ie.getNodeId(), age));
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        String filter = (String) options.get(FILTER);
        String limit = (String) options.get(LIMIT);
        int max = limit == null ? Integer.MAX_VALUE : Integer.parseInt(limit);

        JsonObject root = new JsonObject();

        InflightRepository repo = getCamelContext().getInflightRepository();
        root.put("inflight", repo.size());
        root.put("inflightBrowseEnabled", repo.isInflightBrowseEnabled());
        if (repo.isInflightBrowseEnabled()) {
            final List<JsonObject> list = new ArrayList<>();
            for (InflightRepository.InflightExchange ie : repo.browse(filter, max, false)) {
                JsonObject props = new JsonObject();
                props.put("exchangeId", ie.getExchange().getExchangeId());
                props.put("fromRouteId", ie.getFromRouteId());
                props.put("atRouteId", ie.getAtRouteId());
                props.put("nodeId", ie.getNodeId());
                props.put("elapsed", ie.getElapsed());
                props.put("duration", ie.getDuration());
                list.add(props);
            }
            root.put("exchanges", list);
        }

        return root;
    }
}
