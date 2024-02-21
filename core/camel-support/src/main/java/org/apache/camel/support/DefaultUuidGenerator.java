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
package org.apache.camel.support;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.spi.UuidGenerator;

/**
 * Default {@link UuidGenerator} (32 chars) optimized for Camel usage.
 */
public class DefaultUuidGenerator implements UuidGenerator {

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    private final char[] seed
            = (longToHex(new char[0], ThreadLocalRandom.current().nextLong()).substring(1) + "-").toCharArray();
    private final AtomicLong index = new AtomicLong();

    @Override
    public String generateUuid() {
        return longToHex(seed, index.getAndIncrement());
    }

    private static String longToHex(char[] seed, long v) {
        final int l = seed.length;
        final char[] hexChars = new char[16 + seed.length];
        System.arraycopy(seed, 0, hexChars, 0, l);
        for (int j = 15; j >= 0; j--) {
            hexChars[l + j] = HEX_ARRAY[(int) (v & 0x0F)];
            v >>= 4;
        }
        return new String(hexChars);
    }

}
