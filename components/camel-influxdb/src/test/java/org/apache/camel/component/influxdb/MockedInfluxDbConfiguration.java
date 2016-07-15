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

import java.net.UnknownHostException;

import static junit.framework.TestCase.assertNotNull;

import org.influxdb.InfluxDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import static org.mockito.Mockito.*;

@Configuration
public class MockedInfluxDbConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(MockedInfluxDbConfiguration.class);

    @Bean
    public InfluxDB influxDbBean() throws UnknownHostException {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating new instance of a mocked influx db connection");
        }
        InfluxDB mockedDbConnection = mock(InfluxDB.class);
        //InfluxDB mockedDbConnection = InfluxDBFactory.connect("http://127.0.0.1:8086", "root", "root");
        assertNotNull(mockedDbConnection);
        return mockedDbConnection;
    }


}
