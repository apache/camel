/**
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
package org.apache.camel.spring.boot.actuate.health;

import org.apache.camel.CamelContext;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Camel {@link HealthIndicator}.
 */
public class CamelHealthIndicator extends AbstractHealthIndicator {

    private final CamelContext camelContext;

    public CamelHealthIndicator(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        if (camelContext == null) {
            builder.unknown();
        } else {
            builder.withDetail("name", camelContext.getName());
            builder.withDetail("version", camelContext.getVersion());
            if (camelContext.getUptime() != null) {
                builder.withDetail("uptime", camelContext.getUptime());
                builder.withDetail("uptimeMillis", camelContext.getUptimeMillis());
            }
            builder.withDetail("status", camelContext.getStatus().name());
            if (camelContext.getStatus().isStarted()) {
                builder.up();
            } else if (camelContext.getStatus().isStopped()) {
                builder.down();
            } else {
                builder.unknown();
            }
        }
    }
}
