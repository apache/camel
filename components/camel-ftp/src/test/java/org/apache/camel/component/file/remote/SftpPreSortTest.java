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
package org.apache.camel.component.file.remote;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SftpPreSortTest extends CamelTestSupport {

    @Test
    public void testPreSortDefault() {
        SftpEndpoint endpoint = context.getEndpoint("sftp://hostname/dir", SftpEndpoint.class);
        assertNull(endpoint.getPreSort());
        assertFalse(endpoint.isPreSort());
    }

    @Test
    public void testPreSortTrue() {
        SftpEndpoint endpoint = context.getEndpoint("sftp://hostname/dir?preSort=true", SftpEndpoint.class);
        assertEquals("true", endpoint.getPreSort());
        assertTrue(endpoint.isPreSort());
    }

    @Test
    public void testPreSortFalse() {
        SftpEndpoint endpoint = context.getEndpoint("sftp://hostname/dir?preSort=false", SftpEndpoint.class);
        assertEquals("false", endpoint.getPreSort());
        assertFalse(endpoint.isPreSort());
    }

    @Test
    public void testPreSortName() {
        SftpEndpoint endpoint = context.getEndpoint("sftp://hostname/dir?preSort=name", SftpEndpoint.class);
        assertEquals("name", endpoint.getPreSort());
        assertTrue(endpoint.isPreSort());
    }

    @Test
    public void testPreSortReverseName() {
        SftpEndpoint endpoint = context.getEndpoint("sftp://hostname/dir?preSort=-name", SftpEndpoint.class);
        assertEquals("-name", endpoint.getPreSort());
        assertTrue(endpoint.isPreSort());
    }

    @Test
    public void testPreSortModified() {
        SftpEndpoint endpoint = context.getEndpoint("sftp://hostname/dir?preSort=modified", SftpEndpoint.class);
        assertEquals("modified", endpoint.getPreSort());
        assertTrue(endpoint.isPreSort());
    }

    @Test
    public void testPreSortReverseModified() {
        SftpEndpoint endpoint = context.getEndpoint("sftp://hostname/dir?preSort=-modified", SftpEndpoint.class);
        assertEquals("-modified", endpoint.getPreSort());
        assertTrue(endpoint.isPreSort());
    }

    @Test
    public void testPreSortSize() {
        SftpEndpoint endpoint = context.getEndpoint("sftp://hostname/dir?preSort=size", SftpEndpoint.class);
        assertEquals("size", endpoint.getPreSort());
        assertTrue(endpoint.isPreSort());
    }

    @Test
    public void testPreSortReverseSize() {
        SftpEndpoint endpoint = context.getEndpoint("sftp://hostname/dir?preSort=-size", SftpEndpoint.class);
        assertEquals("-size", endpoint.getPreSort());
        assertTrue(endpoint.isPreSort());
    }

    @Test
    public void testPreSortWithMaxMessagesPerPoll() {
        SftpEndpoint endpoint = context.getEndpoint(
                "sftp://hostname/dir?preSort=modified&maxMessagesPerPoll=10&eagerMaxMessagesPerPoll=true",
                SftpEndpoint.class);
        assertEquals("modified", endpoint.getPreSort());
        assertTrue(endpoint.isPreSort());
        assertEquals(10, endpoint.getMaxMessagesPerPoll());
        assertTrue(endpoint.isEagerMaxMessagesPerPoll());
    }
}
