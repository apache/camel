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

import com.influxdb.client.InfluxDBClient;
import org.apache.camel.CamelContext;
import org.apache.camel.test.junit5.CamelTestSupport;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

public abstract class AbstractInfluxDbTest extends CamelTestSupport {

    InfluxDBClient mockedDbConnection = mock(InfluxDBClient.class, RETURNS_DEEP_STUBS);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("influxDbBean", mockedDbConnection);
        context.getPropertiesComponent().setLocation("classpath:influxdb2.test.properties");
        return context;
    }

}
