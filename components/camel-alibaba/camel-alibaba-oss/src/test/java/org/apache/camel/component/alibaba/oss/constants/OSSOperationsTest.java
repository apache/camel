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
package org.apache.camel.component.alibaba.oss.constants;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OSSOperationsTest {
    @Test
    void testOperations() {
        assertThat(OSSOperations.LIST_BUCKETS).isEqualTo("listBuckets");
        assertThat(OSSOperations.LIST_OBJECTS).isEqualTo("listObjects");
        assertThat(OSSOperations.PUT_OBJECT).isEqualTo("putObject");
        assertThat(OSSOperations.GET_OBJECT).isEqualTo("getObject");
        assertThat(OSSOperations.DELETE_OBJECT).isEqualTo("deleteObject");
        assertThat(OSSOperations.COPY_OBJECT).isEqualTo("copyObject");
        assertThat(OSSOperations.HEAD_OBJECT).isEqualTo("headObject");
    }
}
