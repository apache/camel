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

import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.StartupStep;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "startup-recorder", description = "Starting recording information")
public class StartupRecorderDevConsole extends AbstractDevConsole {

    public record StepEntry(
            @Metadata(description = "The id of the step") int id,
            @Metadata(description = "The id of the parent step") int parentId,
            @Metadata(description = "The step level (sub step of previous steps)") int level,
            @Metadata(description = "Name of the step (only present when known)") String name,
            @Metadata(description = "The source class type of the step") String type,
            @Metadata(description = "Description of the step") String description,
            @Metadata(description = "The begin time (epoch milliseconds)") long beginTime,
            @Metadata(description = "The duration the step took, in milliseconds") long duration) {
    }

    public record Response(
            @Metadata(description = "The startup steps (only present when there are any)") List<StepEntry> steps) {
    }

    public StartupRecorderDevConsole() {
        super("camel", "startup-recorder", "Startup Recorder", "Starting recording information");
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
        List<StepEntry> steps = new ArrayList<>();

        ExtendedCamelContext ecc = getCamelContext().getCamelContextExtension();
        StartupStepRecorder recorder = ecc.getStartupStepRecorder();
        if (recorder != null) {
            recorder.steps().forEach(s -> steps.add(new StepEntry(
                    s.getId(), s.getParentId(), s.getLevel(), s.getName(), s.getType(), s.getDescription(),
                    s.getBeginTime(), s.getDuration())));
        }

        Response response = new Response(steps.isEmpty() ? null : steps);
        return JsonRecordSupport.toJsonObject(response);
    }

    protected String logStep(StartupStep step) {
        long delta = step.getDuration();
        String pad = StringHelper.padString(step.getLevel());
        String out = String.format("%s", pad + step.getType());
        String out2 = String.format("%6s ms", delta);
        String out3;
        if (step.getName() != null) {
            out3 = String.format("%s (%s)", step.getDescription(), step.getName());
        } else {
            out3 = String.format("%s", step.getDescription());
        }
        return String.format("%s : %s - %s", out2, out, out3);
    }

}
