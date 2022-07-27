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
package org.apache.camel.microprofile.health;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;

import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheck.Result;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;

/**
 * Helper utility class for MicroProfile health checks.
 */
final class CamelMicroProfileHealthHelper {

    private CamelMicroProfileHealthHelper() {
        // Utility class
    }

    /**
     * Propagates details from the Camel Health {@link Result} to the MicroProfile {@link HealthCheckResponseBuilder}.
     *
     * @param builder       The health check response builder
     * @param result        The Camel health check result
     * @param exposureLevel The level at which to expose details from the health check result
     */
    public static void applyHealthDetail(HealthCheckResponseBuilder builder, Result result, String exposureLevel) {
        if (!exposureLevel.equals("oneline")) {
            HealthCheck check = result.getCheck();
            Set<String> metaKeys = check.getMetaData().keySet();

            result.getDetails().forEach((key, value) -> {
                if (value != null) {
                    if (exposureLevel.equals("full")) {
                        builder.withData(key, value.toString());
                    } else {
                        // Filter health check metadata to have a less verbose output
                        if (!metaKeys.contains(key)) {
                            builder.withData(key, value.toString());
                        }
                    }
                }
            });

            result.getError().ifPresent(error -> {
                builder.withData("error.message", error.getMessage());

                final StringWriter stackTraceWriter = new StringWriter();
                try (final PrintWriter pw = new PrintWriter(stackTraceWriter, true)) {
                    error.printStackTrace(pw);
                    builder.withData("error.stacktrace", stackTraceWriter.toString());
                }
            });
        }
    }
}
