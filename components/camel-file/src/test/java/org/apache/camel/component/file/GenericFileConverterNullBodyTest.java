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
package org.apache.camel.component.file;

import org.apache.camel.StreamCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies that {@link GenericFileConverter#convertTo} does not throw {@link NullPointerException} when the embedded
 * body of a {@link GenericFile} is null (e.g. a RemoteFile whose content was never retrieved from the remote server),
 * and instead returns {@code null} with a diagnostic WARN log.
 *
 * <p>
 * Real-world trigger: RemoteFile body is null when stream-cache attempts to cache the exchange body before the SFTP
 * consumer has loaded the remote content, producing a bare {@code NullPointerException: null} in Camel 3.x. In Camel 4
 * the NPE was guarded but no diagnostic log was emitted (CAMEL-24563).
 */
public class GenericFileConverterNullBodyTest {

    @Test
    void testNullBodyDoesNotThrowNPEAndReturnsNull() {
        GenericFile<Object> file = new GenericFile<>();
        file.setFileName("CPP_B2BUnits_196498_20260829_094325.xml");
        // body intentionally left null — simulates RemoteFile with unloaded content
        // registry is null because the code returns before reaching it when body is null

        Object result = assertDoesNotThrow(
                () -> GenericFileConverter.convertTo(StreamCache.class, null, file, null),
                "GenericFileConverter.convertTo must not throw NPE when file body is null");

        assertNull(result, "Expected null when GenericFile body is null");
    }

    @Test
    void testNullBodyForStringConversionReturnsNull() {
        GenericFile<Object> file = new GenericFile<>();
        file.setFileName("test.xml");
        // body null, no exchange — returns null before registry is accessed

        Object result = assertDoesNotThrow(
                () -> GenericFileConverter.convertTo(String.class, null, file, null));

        assertNull(result);
    }
}
