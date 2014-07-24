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

import com.box.boxjavalibv2.dao.BoxItem;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box.internal.BoxApiCollection;
import org.apache.camel.component.box.internal.IBoxSharedItemsManagerApiMethod;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for com.box.boxjavalibv2.resourcemanagers.IBoxSharedItemsManager APIs.
 */
public class IBoxSharedItemsManagerIntegrationTest extends AbstractBoxTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(IBoxSharedItemsManagerIntegrationTest.class);
    private static final String PATH_PREFIX = BoxApiCollection.getCollection().getApiName(IBoxSharedItemsManagerApiMethod.class).getName();

    @Test
    public void testGetSharedItem() throws Exception {
        // using com.box.restclientv2.requestsbase.BoxDefaultRequestObject message body for single parameter "defaultRequest"
        BoxItem result = requestBody("direct://GETSHAREDITEM", null);

        assertNotNull("getSharedItem result", result);
        LOG.debug("getSharedItem: " + result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for getSharedItem
                from("direct://GETSHAREDITEM")
                        .to("box://" + PATH_PREFIX + "/getSharedItem?inBody=defaultRequest");

            }
        };
    }
}
