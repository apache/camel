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
package org.apache.camel.component.influxdb2;

import java.util.Map;

import com.influxdb.client.InfluxDBClient;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultComponent;

@org.apache.camel.spi.annotations.Component("influxdb2")
public class InfluxDb2Component extends DefaultComponent {

    @Metadata(autowired = true)
    private InfluxDBClient influxDBClient;

    public InfluxDb2Component() {
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        InfluxDb2Endpoint endpoint = new InfluxDb2Endpoint(uri, this);
        endpoint.setConnectionBean(remaining);
        InfluxDBClient target = influxDBClient;
        if (target == null) {
            // if not using a shared db then lookup
            target = CamelContextHelper.mandatoryLookup(getCamelContext(), remaining, InfluxDBClient.class);
        }
        endpoint.setInfluxDBClient(target);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public InfluxDBClient getInfluxDBClient() {
        return influxDBClient;
    }

    /**
     * The shared Influx DB to use for all endpoints
     *
     * @param influxDBClient
     */
    public void setInfluxDBClient(InfluxDBClient influxDBClient) {
        this.influxDBClient = influxDBClient;
    }
}
