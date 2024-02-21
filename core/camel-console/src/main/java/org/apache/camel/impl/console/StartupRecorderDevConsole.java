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

import java.util.Map;

import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.StartupStep;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole("startup-recorder")
public class StartupRecorderDevConsole extends AbstractDevConsole {

    public StartupRecorderDevConsole() {
        super("camel", "startup-recorder", "Startup Recorder", "Display startup recording");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        ExtendedCamelContext ecc = getCamelContext().getCamelContextExtension();
        StartupStepRecorder recorder = ecc.getStartupStepRecorder();
        if (recorder != null) {
            recorder.steps().forEach(s -> {
                sb.append(logStep(s)).append("\n");
            });
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();

        ExtendedCamelContext ecc = getCamelContext().getCamelContextExtension();
        StartupStepRecorder recorder = ecc.getStartupStepRecorder();
        if (recorder != null) {
            recorder.steps().forEach(s -> {
                JsonObject jo = new JsonObject();
                jo.put("id", s.getId());
                jo.put("parentId", s.getParentId());
                jo.put("level", s.getLevel());
                jo.put("name", s.getName());
                jo.put("type", s.getType());
                jo.put("description", s.getDescription());
                jo.put("beginTime", s.getBeginTime());
                jo.put("duration", s.getDuration());
                arr.add(jo);
            });
        }

        if (!arr.isEmpty()) {
            root.put("steps", arr);
        }
        return root;
    }

    protected String logStep(StartupStep step) {
        long delta = step.getDuration();
        String pad = StringHelper.padString(step.getLevel());
        String out = String.format("%s", pad + step.getType());
        String out2 = String.format("%6s ms", delta);
        String out3 = String.format("%s(%s)", step.getDescription(), step.getName());
        return String.format("%s : %s - %s", out2, out, out3);
    }

}
