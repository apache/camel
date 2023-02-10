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

import org.apache.camel.spi.BacklogTracer;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole("trace")
@Configurer(bootstrap = true)
public class TraceConsole extends AbstractDevConsole {

    public TraceConsole() {
        super("camel", "trace", "Camel Tracing", "Trace routed messages");
    }

    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        BacklogTracer tracer = getCamelContext().getExtension(BacklogTracer.class);
        if (tracer != null) {
            for (BacklogTracerEventMessage t : tracer.dumpAllTracedMessages()) {
                String xml = t.toXml(0);
                sb.append(xml).append("\n");
            }
        }

        return sb.toString();
    }

    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        BacklogTracer tracer = getCamelContext().getExtension(BacklogTracer.class);
        if (tracer != null) {
            List<JsonObject> arr = new ArrayList<>();
            root.put("traces", arr);
            for (BacklogTracerEventMessage t : tracer.dumpAllTracedMessages()) {
                JsonObject jo = (JsonObject) t.asJSon();
                arr.add(jo);
            }
        }

        return root;
    }

}
