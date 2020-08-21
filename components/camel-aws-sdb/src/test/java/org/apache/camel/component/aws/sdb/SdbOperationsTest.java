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
package org.apache.camel.component.aws.sdb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SdbOperationsTest {

    @Test
    public void supportedOperationCount() {
        assertEquals(9, SdbOperations.values().length);
    }

    @Test
    public void valueOf() {
        assertEquals(SdbOperations.BatchDeleteAttributes, SdbOperations.valueOf("BatchDeleteAttributes"));
        assertEquals(SdbOperations.BatchPutAttributes, SdbOperations.valueOf("BatchPutAttributes"));
        assertEquals(SdbOperations.DeleteAttributes, SdbOperations.valueOf("DeleteAttributes"));
        assertEquals(SdbOperations.DeleteDomain, SdbOperations.valueOf("DeleteDomain"));
        assertEquals(SdbOperations.DomainMetadata, SdbOperations.valueOf("DomainMetadata"));
        assertEquals(SdbOperations.GetAttributes, SdbOperations.valueOf("GetAttributes"));
        assertEquals(SdbOperations.ListDomains, SdbOperations.valueOf("ListDomains"));
        assertEquals(SdbOperations.PutAttributes, SdbOperations.valueOf("PutAttributes"));
        assertEquals(SdbOperations.Select, SdbOperations.valueOf("Select"));
    }

    @Test
    public void testToString() {
        assertEquals("BatchDeleteAttributes", SdbOperations.BatchDeleteAttributes.toString());
        assertEquals("BatchPutAttributes", SdbOperations.BatchPutAttributes.toString());
        assertEquals("DeleteAttributes", SdbOperations.DeleteAttributes.toString());
        assertEquals("DeleteDomain", SdbOperations.DeleteDomain.toString());
        assertEquals("DomainMetadata", SdbOperations.DomainMetadata.toString());
        assertEquals("GetAttributes", SdbOperations.GetAttributes.toString());
        assertEquals("ListDomains", SdbOperations.ListDomains.toString());
        assertEquals("PutAttributes", SdbOperations.PutAttributes.toString());
        assertEquals("Select", SdbOperations.Select.toString());
    }
}
