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
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class CronPatternsTest extends CamelTestSupport {

    @ParameterizedTest
    @ValueSource(strings = {
            "cron:tab?schedule=0/1 * * * * ? 1 2", "cron:tab?schedule=wrong pattern",
            "cron://name?schedule=0+0/5+12-18+?+*+MON-FRI+2019+1" })
    @DisplayName("Test parsing with too many, too little and invalid characters in the pattern")
    void testParts(String endpointUri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(endpointUri)
                        .to("mock:result");
            }
        });
        assertThrows(FailedToCreateRouteException.class, () -> {
            context.start();
        });
    }

    @Test
    void testPlusInURI() throws Exception {
        BeanIntrospection bi = PluginHelper.getBeanIntrospection(context);
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

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

}
