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

import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class CronPatternsTest extends CamelTestSupport {

    @Test
    void testTooManyParts() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("cron:tab?schedule=0/1 * * * * ? 1 2")
                        .to("mock:result");
            }
        });
        assertThrows(FailedToCreateRouteException.class, () -> {
            context.start();
        });
    }

    @Test
    void testTooLittleParts() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("cron:tab?schedule=wrong pattern")
                        .to("mock:result");
            }
        });
        assertThrows(FailedToCreateRouteException.class, () -> {
            context.start();
        });
    }

    @Test
    void testPlusInURI() throws Exception {
        BeanIntrospection bi = context.adapt(ExtendedCamelContext.class).getBeanIntrospection();
        bi.setExtendedStatistics(true);
        bi.setLoggingLevel(LoggingLevel.INFO);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("cron://name?schedule=0+0/5+12-18+?+*+MON-FRI")
                        .to("mock:result");
            }
        });
        context.start();

        Thread.sleep(5);

        context.stop();

        Assertions.assertEquals(0, bi.getInvokedCounter());
    }

    @Test
    void testPlusInURINok() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("cron://name?schedule=0+0/5+12-18+?+*+MON-FRI+2019+1")
                        .to("mock:result");
            }
        });
        assertThrows(FailedToCreateRouteException.class, () -> {
            context.start();
        });
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

}
