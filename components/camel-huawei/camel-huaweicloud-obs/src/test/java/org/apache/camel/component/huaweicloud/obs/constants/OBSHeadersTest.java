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
package org.apache.camel.component.huaweicloud.obs.constants;

import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OBSHeadersTest {
    @Test
    public void testHeaders() {
        assertEquals("bucketName", OBSHeaders.BUCKET_NAME);
        assertEquals("objectKey", OBSHeaders.OBJECT_KEY);
        assertEquals("lastModified", OBSHeaders.LAST_MODIFIED);
        assertEquals("contentLength", OBSHeaders.CONTENT_LENGTH);
        assertEquals("contentType", OBSHeaders.CONTENT_TYPE);
        assertEquals("etag", OBSHeaders.ETAG);
        assertEquals("contentMd5", OBSHeaders.CONTENT_MD5);
    }
}
