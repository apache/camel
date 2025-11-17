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

import java.util.Collection;
import java.util.Map;

import org.apache.camel.spi.BacklogTracer;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "message-history", displayName = "Message History", description = "History of latest completed exchange")
@Configurer(extended = true)
public class MessageHistoryDevConsole extends AbstractDevConsole {

    public MessageHistoryDevConsole() {
        super("camel", "message-history", "Message History", "History of latest completed exchange");
    }

    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        BacklogTracer tracer = getCamelContext().getCamelContextExtension().getContextPlugin(BacklogTracer.class);
        if (tracer != null) {
            Collection<BacklogTracerEventMessage> queue = tracer.getLatestMessageHistory();
            for (BacklogTracerEventMessage t : queue) {
                String json = t.toJSon(0);
                sb.append(json).append("\n");
            }
        }

        return sb.toString();
    }

    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        BacklogTracer tracer = getCamelContext().getCamelContextExtension().getContextPlugin(BacklogTracer.class);
        if (tracer != null) {
            JsonArray arr = new JsonArray();

            Collection<BacklogTracerEventMessage> queue = tracer.getLatestMessageHistory();
            for (BacklogTracerEventMessage t : queue) {
                JsonObject jo = (JsonObject) t.asJSon();
                arr.add(jo);
            }
            root.put("name", getCamelContext().getName());
            root.put("traces", arr);
        }

        return root;
    }

}
