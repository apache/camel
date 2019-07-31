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

import org.junit.Assert;
import org.junit.Test;

public class ReferenceCountTest {

    @Test
    public void testReferenceCount() {
        AtomicInteger cnt = new AtomicInteger(0);

        ReferenceCount ref = ReferenceCount.on(cnt::incrementAndGet, cnt::decrementAndGet);

        ref.retain();
        Assert.assertEquals(1, ref.get());
        Assert.assertEquals(1, cnt.get());

        ref.retain();
        Assert.assertEquals(2, ref.get());
        Assert.assertEquals(1, cnt.get());

        ref.release();
        Assert.assertEquals(1, ref.get());
        Assert.assertEquals(1, cnt.get());

        ref.release();
        Assert.assertEquals(0, ref.get());
        Assert.assertEquals(0, cnt.get());
    }
}
