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
package org.apache.camel.component.openstack.it;

import java.util.Map;

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.swift.SwiftConstants;
import org.junit.jupiter.api.Test;
import org.openstack4j.model.storage.object.SwiftContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OpenstackSwiftContainerTest extends OpenstackWiremockTestSupport {

    private static final String URI_FORMAT
            = "openstack-swift://%s?username=user&password=secret&project=project&operation=%s&subsystem="
              + SwiftConstants.SWIFT_SUBSYSTEM_CONTAINERS;

    private static final String CONTAINER_NAME = "myContainer";
    private static final String NAME_BOOK = "Book";
    private static final String NAME_YEAR = "Year";

    @Test
    void getAllShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET_ALL);
        SwiftContainer[] containers = template.requestBody(uri, null, SwiftContainer[].class);

        assertNotNull(containers);
        assertEquals(2, containers.length);
        assertEquals(100, containers[0].getTotalSize());
        assertEquals("Test", containers[0].getName());
        assertEquals("marktwain", containers[1].getName());
    }

    @Test
    void getMetadataShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), SwiftConstants.GET_METADATA);
        Map<?, ?> metadata = template.requestBodyAndHeader(uri, null, SwiftConstants.CONTAINER_NAME, CONTAINER_NAME, Map.class);

        assertNotNull(metadata);
        assertEquals("2000", metadata.get(NAME_YEAR));
        assertEquals("TestBook", metadata.get(NAME_BOOK));
    }

}
