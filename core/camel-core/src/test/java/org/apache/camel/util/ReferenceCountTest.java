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
package org.apache.camel.util;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReferenceCountTest {

    @Test
    public void testReferenceCount() {
        AtomicInteger cnt = new AtomicInteger();

        ReferenceCount ref = ReferenceCount.on(cnt::incrementAndGet, cnt::decrementAndGet);

        ref.retain();
        assertEquals(1, ref.get());
        assertEquals(1, cnt.get());

        ref.retain();
        assertEquals(2, ref.get());
        assertEquals(1, cnt.get());

        ref.release();
        assertEquals(1, ref.get());
        assertEquals(1, cnt.get());

        ref.release();
        assertEquals(0, ref.get());
        assertEquals(0, cnt.get());
    }
}
