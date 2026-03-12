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
package org.apache.camel.telemetry.decorators;

import java.time.Instant;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.spi.BrowsableEndpoint.BrowseStatus;
import org.apache.camel.telemetry.Span;

public class SedaSpanDecorator extends AbstractInternalSpanDecorator {

    @Override
    public String getComponent() {
        return "seda";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.seda.SedaComponent";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);
        if (endpoint instanceof BrowsableEndpoint browsableEndpoint) {
            BrowseStatus browsStatus = browsableEndpoint.getBrowseStatus(0);
            span.setTag("seda.queue.size.current", String.valueOf(browsStatus.size()));
            if (browsStatus.firstTimestamp() != 0) {
                span.setTag("seda.queue.first.timestamp", Instant.ofEpochMilli(browsStatus.firstTimestamp()).toString());
            }
            if (browsStatus.lastTimestamp() != 0) {
                span.setTag("seda.queue.last.timestamp", Instant.ofEpochMilli(browsStatus.lastTimestamp()).toString());
            }
        }
    }

}
