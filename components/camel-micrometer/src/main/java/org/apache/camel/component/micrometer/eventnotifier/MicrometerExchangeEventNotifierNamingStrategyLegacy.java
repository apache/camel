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
package org.apache.camel.component.micrometer.eventnotifier;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.micrometer.MicrometerUtils;

import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_EXCHANGE_EVENT_METER_NAME;

public class MicrometerExchangeEventNotifierNamingStrategyLegacy implements MicrometerExchangeEventNotifierNamingStrategy {

    boolean endpointBaseURI = true;

    public MicrometerExchangeEventNotifierNamingStrategyLegacy() {

    }

    public MicrometerExchangeEventNotifierNamingStrategyLegacy(boolean endpointBaseURI) {
        this.endpointBaseURI = endpointBaseURI;
    }

    @Override
    public String getName(Exchange exchange, Endpoint endpoint) {
        return formatName(DEFAULT_CAMEL_EXCHANGE_EVENT_METER_NAME);
    }

    @Override
    public String formatName(String name) {
        return MicrometerUtils.legacyName(name);
    }

    @Override
    public boolean isBaseEndpointURI() {
        return endpointBaseURI;
    }

}
