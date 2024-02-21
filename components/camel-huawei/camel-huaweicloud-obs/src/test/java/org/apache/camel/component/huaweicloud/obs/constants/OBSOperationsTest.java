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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OBSOperationsTest {
    @Test
    public void testOperations() {
        assertEquals("listBuckets", OBSOperations.LIST_BUCKETS);
        assertEquals("createBucket", OBSOperations.CREATE_BUCKET);
        assertEquals("deleteBucket", OBSOperations.DELETE_BUCKET);
        assertEquals("checkBucketExists", OBSOperations.CHECK_BUCKET_EXISTS);
        assertEquals("getBucketMetadata", OBSOperations.GET_BUCKET_METADATA);
        assertEquals("listObjects", OBSOperations.LIST_OBJECTS);
    }
}
