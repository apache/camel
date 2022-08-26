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

import java.util.Date;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

@DevConsole("context")
public class ContextDevConsole extends AbstractDevConsole {

    public ContextDevConsole() {
        super("camel", "context", "CamelContext", "Overall information about the CamelContext");
    }

    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("Apache Camel %s %s (%s) uptime %s", getCamelContext().getVersion(),
                getCamelContext().getStatus().name().toLowerCase(Locale.ROOT), getCamelContext().getName(),
                getCamelContext().getUptime()));
        sb.append("\n");

        ManagedCamelContext mcc = getCamelContext().getExtension(ManagedCamelContext.class);
        if (mcc != null) {
            ManagedCamelContextMBean mb = mcc.getManagedCamelContext();
            sb.append(String.format("\n    Total: %s", mb.getExchangesTotal()));
            sb.append(String.format("\n    Failed: %s", mb.getExchangesFailed()));
            sb.append(String.format("\n    Inflight: %s", mb.getExchangesInflight()));
            sb.append(String.format("\n    Mean Time: %s", TimeUtils.printDuration(mb.getMeanProcessingTime(), true)));
            sb.append(String.format("\n    Max Time: %s", TimeUtils.printDuration(mb.getMaxProcessingTime(), true)));
            sb.append(String.format("\n    Min Time: %s", TimeUtils.printDuration(mb.getMinProcessingTime(), true)));
            Date last = mb.getLastExchangeCreatedTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                sb.append(String.format("\n    Since Last: %s", ago));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();
        root.put("name", getCamelContext().getName());
        root.put("version", getCamelContext().getVersion());
        root.put("state", getCamelContext().getStatus().name());
        root.put("phase", getCamelContext().adapt(ExtendedCamelContext.class).getStatusPhase());
        root.put("uptime", getCamelContext().getUptime());

        ManagedCamelContext mcc = getCamelContext().getExtension(ManagedCamelContext.class);
        if (mcc != null) {
            ManagedCamelContextMBean mb = mcc.getManagedCamelContext();
            JsonObject stats = new JsonObject();
            stats.put("exchangesTotal", mb.getExchangesTotal());
            stats.put("exchangesFailed", mb.getExchangesFailed());
            stats.put("exchangesInflight", mb.getExchangesInflight());
            stats.put("meanProcessingTime", mb.getMeanProcessingTime());
            stats.put("maxProcessingTime", mb.getMaxProcessingTime());
            stats.put("minProcessingTime", mb.getMinProcessingTime());
            Date last = mb.getLastExchangeCreatedTimestamp();
            if (last != null) {
                String ago = TimeUtils.printSince(last.getTime());
                stats.put("sinceLastExchange", ago);
            }
            root.put("statistics", stats);
        }

        return root;
    }

}
