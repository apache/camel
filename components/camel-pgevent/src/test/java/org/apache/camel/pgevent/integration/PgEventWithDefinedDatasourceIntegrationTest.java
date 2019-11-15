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
package org.apache.camel.pgevent.integration;

import java.util.Properties;

import com.impossibl.postgres.jdbc.PGDataSource;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class PgEventWithDefinedDatasourceIntegrationTest extends AbstractPgEventIntegrationTest {

    @EndpointInject("pgevent:///{{database}}/testchannel?datasource=#pgDataSource")
    private Endpoint subscribeEndpoint;

    @EndpointInject("pgevent:///{{database}}/testchannel?datasource=#pgDataSource")
    private Endpoint notifyEndpoint;

    @EndpointInject("timer://test?repeatCount=1&period=1")
    private Endpoint timerEndpoint;

    @EndpointInject("mock:result")
    private MockEndpoint mockEndpoint;

    @BindToRegistry("pgDataSource")
    public PGDataSource loadDataSource() throws Exception {
        Properties properties = new Properties();
        properties.load(getClass().getResourceAsStream("/test-options.properties"));

        PGDataSource dataSource = new PGDataSource();
        dataSource.setHost(properties.getProperty("host"));
        dataSource.setPort(Integer.parseInt(properties.getProperty("port")));
        dataSource.setDatabaseName(properties.getProperty("database"));
        dataSource.setUser(properties.getProperty("userName"));
        dataSource.setPassword(properties.getProperty("password"));


        return dataSource;
    }

    @Test
    public void testPgEventPublishSubscribeWithDefinedDatasource() throws Exception {
        mockEndpoint.expectedBodiesReceived(TEST_MESSAGE_BODY);
        mockEndpoint.assertIsSatisfied(5000);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(timerEndpoint)
                    .setBody(constant(TEST_MESSAGE_BODY))
                    .to(notifyEndpoint);

                from(subscribeEndpoint)
                    .to(mockEndpoint);
            }
        };
    }
}
