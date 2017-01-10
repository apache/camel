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
package org.apache.camel.component.influxdb;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfluxDbComponent extends UriEndpointComponent {

    private static final Logger LOG = LoggerFactory.getLogger(InfluxDbComponent.class);

    public InfluxDbComponent() {
        super(InfluxDbEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        InfluxDbEndpoint endpoint = new InfluxDbEndpoint(uri, this);
        endpoint.setConnectionBean(remaining);
        return endpoint;
    }
}
