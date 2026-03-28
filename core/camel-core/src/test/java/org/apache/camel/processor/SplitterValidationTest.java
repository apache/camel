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
package org.apache.camel.processor;

import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitterValidationTest extends ContextTestSupport {

    @Test
    void testErrorThresholdNegativeIsRejected() {
        Exception e = assertThrows(Exception.class, () -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .split(body()).errorThreshold(-0.1)
                        .to("mock:split");
            }
        }));
        assertTrue(findIllegalArgumentException(e, "errorThreshold"));
    }

    @Test
    void testErrorThresholdAboveOneIsRejected() {
        Exception e = assertThrows(Exception.class, () -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .split(body()).errorThreshold(1.5)
                        .to("mock:split");
            }
        }));
        assertTrue(findIllegalArgumentException(e, "errorThreshold"));
    }

    @Test
    void testMaxFailedRecordsNegativeIsRejected() {
        Exception e = assertThrows(Exception.class, () -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .split(body()).maxFailedRecords(-1)
                        .to("mock:split");
            }
        }));
        assertTrue(findIllegalArgumentException(e, "maxFailedRecords"));
    }

    @Test
    void testWatermarkKeyWithoutResumeStrategyIsRejected() {
        Exception e = assertThrows(Exception.class, () -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .split(body()).watermarkKey("someKey")
                        .to("mock:split");
            }
        }));
        assertTrue(findIllegalArgumentException(e, "resumeStrategy"));
    }

    @Test
    void testResumeStrategyWithoutKeyIsRejected() {
        context.getRegistry().bind("myStrategy",
                new SplitterTestResumeStrategy(new ConcurrentHashMap<>()));
        Exception e = assertThrows(Exception.class, () -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .split(body()).resumeStrategy("myStrategy")
                        .to("mock:split");
            }
        }));
        assertTrue(findIllegalArgumentException(e, "resumeStrategy"));
    }

    @Test
    void testWatermarkExpressionWithoutResumeStrategyIsRejected() {
        Exception e = assertThrows(Exception.class, () -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .split(body()).watermarkExpression("${body}")
                        .to("mock:split");
            }
        }));
        assertTrue(findIllegalArgumentException(e, "watermarkExpression"));
    }

    private boolean findIllegalArgumentException(Throwable e, String keyword) {
        while (e != null) {
            if (e instanceof IllegalArgumentException && e.getMessage() != null && e.getMessage().contains(keyword)) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }
}
