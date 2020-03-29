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
package org.apache.camel.component.influxdb;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultComponent;
import org.influxdb.InfluxDB;

@Component("influxdb")
public class InfluxDbComponent extends DefaultComponent {

    private InfluxDB influxDB;

    public InfluxDbComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        InfluxDbEndpoint endpoint = new InfluxDbEndpoint(uri, this);
        endpoint.setConnectionBean(remaining);
        InfluxDB target = influxDB;
        if (target == null) {
            // if not using a shared db then lookup
            target = CamelContextHelper.mandatoryLookup(getCamelContext(), remaining, InfluxDB.class);
        }
        endpoint.setInfluxDB(target);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public InfluxDB getInfluxDB() {
        return influxDB;
    }

    /**
     * The shared Influx DB to use for all endpoints
     */
    public void setInfluxDB(InfluxDB influxDB) {
        this.influxDB = influxDB;
    }
}
