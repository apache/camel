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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Map;

import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.spi.annotations.DevConsole;

@DevConsole("health")
public class HealthDevConsole extends AbstractDevConsole {

    public HealthDevConsole() {
        super("camel", "health", "Health Check", "Health Check Status");
    }

    @Override
    protected Object doCall(MediaType mediaType, Map<String, Object> options) {
        // only text is supported
        StringBuilder sb = new StringBuilder();

        Collection<HealthCheck.Result> results = HealthCheckHelper.invoke(getCamelContext());
        boolean up = results.stream().allMatch(h -> HealthCheck.State.UP.equals(h.getState()));
        sb.append(String.format("Health Check Status: %s", up ? "UP" : "DOWN"));
        sb.append("\n");

        results.forEach(res -> {
            boolean ok = res.getState().equals(HealthCheck.State.UP);
            if (ok) {
                sb.append(String.format("\n    %s: %s", res.getCheck().getId(), res.getState()));
            } else {
                String msg = res.getMessage().orElse("");
                sb.append(String.format("\n    %s: %s (%s)", res.getCheck().getId(), res.getState(), msg));
                Throwable cause = res.getError().orElse(null);
                if (cause != null) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    cause.printStackTrace(pw);
                    sb.append(pw);
                    sb.append("\n\n");
                }
            }
        });

        return sb.toString();
    }
}
