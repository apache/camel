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

package org.apache.camel.component.wal.exceptions;

import java.io.IOException;

import static java.lang.String.format;

public class BufferTooSmallException extends IOException {
    private final int remaining;
    private final int requested;

    public BufferTooSmallException(int remaining, int requested) {
        super(format("There is not enough space on the buffer for an offset entry: %d bytes remaining, %d bytes needed",
                remaining, requested));

        this.remaining = remaining;
        this.requested = requested;
    }

    public int getRemaining() {
        return remaining;
    }

    public int getRequested() {
        return requested;
    }
}
