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
package org.apache.camel.impl.health;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.health.HealthCheckResultBuilder;

public final class ContextHealthCheck extends AbstractHealthCheck implements CamelContextAware {
    private CamelContext camelContext;

    public ContextHealthCheck() {
        super("camel", "context");
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        builder.unknown();

        if (camelContext != null) {
            builder.detail("context.name", camelContext.getName());
            builder.detail("context.version", camelContext.getVersion());
            builder.detail("context.status", camelContext.getStatus().name());

            if (camelContext.getStatus().isStarted()) {
                builder.up();
            } else if (camelContext.getStatus().isStopped()) {
                builder.down();
            }
        }
    }
}
