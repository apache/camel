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
package org.apache.camel.opentracing.decorators;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

public class StompSpanDecorator extends AbstractMessagingSpanDecorator {

    protected static final String QUEUE_PREFIX = "queue:";

    @Override
    public String getComponent() {
        return "stomp";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.stomp.StompComponent";
    }

    @Override
    public String getDestination(Exchange exchange, Endpoint endpoint) {
        String destination = super.getDestination(exchange, endpoint);
        if (destination.startsWith(QUEUE_PREFIX)) {
            destination = destination.substring(QUEUE_PREFIX.length());
        }
        return destination;
    }

}
