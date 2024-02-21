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
package org.apache.camel.component.box;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box.api.BoxSearchManager;
import org.apache.camel.component.box.internal.BoxApiCollection;
import org.apache.camel.component.box.internal.BoxSearchManagerApiMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test class for {@link BoxSearchManager} APIs.
 */
@EnabledIf(value = "org.apache.camel.component.box.AbstractBoxITSupport#hasCredentials",
           disabledReason = "Box credentials were not provided")
public class BoxSearchManagerIT extends AbstractBoxITSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BoxSearchManagerIT.class);
    private static final String PATH_PREFIX = BoxApiCollection.getCollection()
            .getApiName(BoxSearchManagerApiMethod.class).getName();

    /**
     * There is a delay 5-10 mins between upload of file and being searchable (probably to update some search indexes,
     * see https://community.box.com/t5/Platform-and-Development-Forum/Box-Search-Delay/td-p/40072). With this delay
     * search for an actual item, created in the beginning of the test, doesn't make sense. Test only verifies, that
     * search results works (and does not end with an exception).
     *
     * To test search of real data, change query string from '*' to real name of file.
     */
    @Test
    public void testSearchFolder() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.folderId", "0");
        // parameter type is String
        headers.put("CamelBox.query", "*");

        @SuppressWarnings("rawtypes")
        final java.util.Collection result = requestBodyAndHeaders("direct://SEARCHFOLDER", null, headers);

        assertNotNull(result, "searchFolder result");
        LOG.debug("searchFolder: {}", result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for searchFolder
                from("direct://SEARCHFOLDER").to("box://" + PATH_PREFIX + "/searchFolder");
            }
        };
    }

}
