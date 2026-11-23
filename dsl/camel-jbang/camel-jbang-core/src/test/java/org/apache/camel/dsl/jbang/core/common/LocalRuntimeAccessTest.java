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
package org.apache.camel.dsl.jbang.core.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocalRuntimeAccessTest {

    @Test
    void aPidTargetHasAnOpaqueFileTransportIdentity() {
        RuntimeTarget target = LocalRuntimeAccess.INSTANCE.targetForPid(4711);

        assertNotNull(target);
        assertEquals("file:4711", target.id());
        assertEquals(Long.valueOf(4711L), target.pid());
        assertEquals("file", target.transport());
    }

    @Test
    void aRemoteTargetDoesNotNeedASyntheticPid() {
        RuntimeTarget target = new RuntimeTarget("websocket:tenant-a/orders-1", null, "orders", "camel", "websocket");

        assertNull(target.pid());
        assertEquals("ID websocket:tenant-a/orders-1", target.identityLabel());
    }

    @Test
    void discoveredLocalProcessesAreMappedWithoutChangingTheirMetadata() {
        RuntimeHelper.ProcessInfo process = new RuntimeHelper.ProcessInfo(4712, "orders", "camel");
        RuntimeTarget target = RuntimeTarget.local(process);

        assertEquals("file:4712", target.id());
        assertEquals(Long.valueOf(4712L), target.pid());
        assertEquals("orders", target.name());
        assertEquals("camel", target.contextName());
    }
}
