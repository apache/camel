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
package org.apache.camel.spring.boot.actuate.info;

import org.apache.camel.CamelContext;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

/**
 * Camel {@link InfoContributor}
 */
public class CamelInfoContributor implements InfoContributor {

    private final CamelContext camelContext;

    public CamelInfoContributor(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void contribute(Info.Builder builder) {
        if (camelContext != null) {
            builder.withDetail("camel.name", camelContext.getName());
            builder.withDetail("camel.version", camelContext.getVersion());
            if (camelContext.getUptime() != null) {
                builder.withDetail("camel.uptime", camelContext.getUptime());
                builder.withDetail("camel.uptimeMillis", camelContext.getUptimeMillis());
            }
            builder.withDetail("camel.status", camelContext.getStatus().name());
        }
    }
}
