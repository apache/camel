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
package org.apache.camel.component.box;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box.api.BoxEventLogsManager;
import org.apache.camel.component.box.internal.BoxApiCollection;
import org.apache.camel.component.box.internal.BoxEventLogsManagerApiMethod;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for
 * {@link BoxEventLogsManager} APIs.
 */
public class BoxEventLogsManagerIntegrationTest extends AbstractBoxTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BoxEventLogsManagerIntegrationTest.class);
    private static final String PATH_PREFIX = BoxApiCollection.getCollection()
            .getApiName(BoxEventLogsManagerApiMethod.class).getName();
    private static final long ONE_MINUTE_OF_MILLISECONDS = 1000 * 60;

    @Ignore // Requires enterprise admin account to test
    @Test
    public void testGetEnterpriseEvents() throws Exception {
        Date before = new Date();
        Date after = new Date();
        after.setTime(before.getTime() - ONE_MINUTE_OF_MILLISECONDS);

        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBox.position", null);
        // parameter type is java.util.Date
        headers.put("CamelBox.after", after);
        // parameter type is java.util.Date
        headers.put("CamelBox.before", before);
        // parameter type is com.box.sdk.BoxEvent.Type[]
        headers.put("CamelBox.types", null);

        @SuppressWarnings("rawtypes")
        final java.util.List result = requestBodyAndHeaders("direct://GETENTERPRISEEVENTS", null, headers);

        assertNotNull("getEnterpriseEvents result", result);
        LOG.debug("getEnterpriseEvents: " + result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for getEnterpriseEvents
                from("direct://GETENTERPRISEEVENTS").to("box://" + PATH_PREFIX + "/getEnterpriseEvents");

            }
        };
    }
}
