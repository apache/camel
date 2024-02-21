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
package org.apache.camel.builder.endpoint;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DumpRoutesTest extends BaseEndpointDslTest {
    private static final Logger LOG = LoggerFactory.getLogger(DumpRoutesTest.class);
    private static final String TEST_DATA_DIR = BaseEndpointDslTest.generateUniquePath(DumpRoutesTest.class);

    @Test
    public void testDumpModelAsXml() throws Exception {
        String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, context.getRouteDefinition("myRoute"));
        assertNotNull(xml);
        LOG.info(xml);

        assertTrue(xml
                .contains(
                        "file://" + TEST_DATA_DIR + "?delay=2&amp;delete=true&amp;maxMessagesPerPoll=1&amp;timeUnit=SECONDS"));
        assertTrue(xml.contains("mock://result"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new EndpointRouteBuilder() {
            public void configure() throws Exception {
                from(file(TEST_DATA_DIR).delay(2).timeUnit(TimeUnit.SECONDS).delete(true).maxMessagesPerPoll(1))
                        .routeId("myRoute")
                        .convertBodyTo(String.class)
                        .to(mock("result"));
            }
        };
    }

}
