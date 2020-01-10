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
package org.apache.camel.component.cron;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CronPatternsTest extends CamelTestSupport {

    @Test(expected = FailedToCreateRouteException.class)
    public void testTooManyParts() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("cron:tab?schedule=0/1 * * * * ? 1 2")
                        .to("mock:result");
            }
        });
        context.start();
    }

    @Test(expected = FailedToCreateRouteException.class)
    public void testTooLittleParts() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("cron:tab?schedule=wrong pattern")
                        .to("mock:result");
            }
        });
        context.start();
    }

    @Test
    public void testPlusInURI() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("cron://name?schedule=0+0/5+12-18+?+*+MON-FRI")
                        .to("mock:result");
            }
        });
        context.start();
    }

    @Test(expected = FailedToCreateRouteException.class)
    public void testPlusInURINok() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("cron://name?schedule=0+0/5+12-18+?+*+MON-FRI+2019+1")
                        .to("mock:result");
            }
        });
        context.start();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

}
