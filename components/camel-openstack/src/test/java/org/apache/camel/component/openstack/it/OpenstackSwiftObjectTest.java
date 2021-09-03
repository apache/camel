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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.swift.SwiftConstants;
import org.junit.jupiter.api.Test;
import org.openstack4j.model.storage.object.SwiftObject;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OpenstackSwiftObjectTest extends OpenstackWiremockTestSupport {

    private static final String URI_FORMAT
            = "openstack-swift://%s?username=user&password=secret&project=project&operation=%s&subsystem="
              + SwiftConstants.SWIFT_SUBSYSTEM_OBJECTS;

    private static final String OBJECT_CONTAINER_NAME = "test-container";
    private static final String OBJECT_NAME = "test-file";

    @Test
    void getShouldSucceed() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(SwiftConstants.CONTAINER_NAME, OBJECT_CONTAINER_NAME);
        headers.put(SwiftConstants.OBJECT_NAME, OBJECT_NAME);

        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET);
        SwiftObject swiftObject = template.requestBodyAndHeaders(uri, null, headers, SwiftObject.class);

        assertEquals(OBJECT_CONTAINER_NAME, swiftObject.getContainerName());
        assertEquals(OBJECT_NAME, swiftObject.getName());
        assertEquals(15, swiftObject.getSizeInBytes());
        assertEquals("application/json", swiftObject.getMimeType());
        assertEquals("12345678901234567890", swiftObject.getETag());
    }

}
