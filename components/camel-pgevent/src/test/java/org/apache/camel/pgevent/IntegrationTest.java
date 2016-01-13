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
package org.apache.camel.pgevent;

import com.impossibl.postgres.jdbc.PGDataSource;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.junit.Before;
import org.junit.Test;

public class IntegrationTest {

    private String host;
    private String port;
    private String database;
    private String user;
    private String password;

    private Main main;

    private PGDataSource ds;

    @Before
    public void setUp() throws Exception {
        this.host = System.getProperty("pgjdbc.test.server", "localhost");
        this.port = System.getProperty("pgjdbc.test.port", "5432");
        this.database = System.getProperty("pgjdbc.test.db", "event_tests");
        this.user = System.getProperty("pgjdbc.test.user", "dphillips");
        this.password = System.getProperty("pgjdbc.test.password");

        ds = new PGDataSource();
        ds.setHost(this.host);
        ds.setPort(Integer.parseInt(this.port));
        ds.setDatabase(this.database);
        ds.setUser(this.user);
        if (this.password != null) {
            ds.setPassword(this.password);
        }

        main = new Main();
        main.bind("test", ds);
        main.addRouteBuilder(buildConsumer());
        main.addRouteBuilder(buildProducer());
    }

    RouteBuilder buildConsumer() {
        RouteBuilder builder = new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                fromF("pgevent://%s:%s/%s/testchannel?user=%s&pass=%s", host, port, database, user, password)
                    .to("log:org.apache.camel.pgevent.PgEventConsumer?level=DEBUG");
            }
        };

        return builder;
    }

    RouteBuilder buildProducer() {
        RouteBuilder builder = new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("timer://test?fixedRate=true&period=5000")
                    .setBody(header(Exchange.TIMER_FIRED_TIME))
                    .toF("pgevent://%s:%s/%s/testchannel?user=%s&pass=%s", host, port, database, user, password);
            }
        };

        return builder;
    }

    @Test
    public void waitHere() throws Exception {
        main.run();
    }
}
