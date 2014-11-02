/*
 * Copyright 2014 Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import org.apache.camel.component.pgevent.PgEventEndpoint;
import org.apache.camel.component.pgevent.PgEventComponent;
import com.impossibl.postgres.jdbc.PGDataSource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.log.LogComponent;
import org.apache.camel.component.timer.TimerComponent;
import org.apache.camel.main.Main;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author dphillips
 */
public class IntegrationTest {

    private Main main;

    private PGDataSource ds;
    
    @Before
    public void setUp() throws Exception {
        ds = new PGDataSource();
        ds.setHost(System.getProperty("pgjdbc.test.server", "localhost"));
        ds.setPort(Integer.parseInt(System.getProperty("pgjdbc.test.port", "5432")));
        ds.setDatabase(System.getProperty("pgjdbc.test.db", "event_tests"));
        ds.setUser(System.getProperty("pgjdbc.test.user", "dphillips"));
        
        main = new Main();
        main.bind("pgevent", new PgEventComponent(PgEventEndpoint.class));
        main.bind("log", new LogComponent());
        main.bind("timer", new TimerComponent());
        main.enableHangupSupport();
        main.bind("test", ds);
        main.addRouteBuilder(buildConsumer());
//        main.addRouteBuilder(buildProducer());
    }

    RouteBuilder buildConsumer() {
        RouteBuilder builder = new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("pgevent://127.0.0.1:5432/event_tests/testchannel?user=dphillips")
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
                    .to("pgevent://127.0.0.1:5432/event_tests/testchannel?user=dphillips");
            }
        };
        
        return builder;
    }

    @Test
    public void waitHere() throws Exception {
        main.run();
    }
}
