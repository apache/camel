/**
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
package org.apache.camel.component.http.helper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Subclass of ByteArrayOutputStream that allows creation of a
 * ByteArrayInputStream directly without creating a copy of the byte[].
 *
 * Also, on "toByteArray()" it truncates it's buffer to the current size
 * and returns the new buffer directly.  Multiple calls to toByteArray()
 * will return the exact same byte[] unless a write is called in between.
 *
 * Note: once the InputStream is created, the output stream should
 * no longer be used.  In particular, make sure not to call reset()
 * and then write as that may overwrite the data that the InputStream
 * is using.
 */
public class LoadingByteArrayOutputStream extends ByteArrayOutputStream {
    public LoadingByteArrayOutputStream() {
        super(1024);
    }
    public LoadingByteArrayOutputStream(int i) {
        super(i);
    }

    public ByteArrayInputStream createInputStream() {
        return new ByteArrayInputStream(buf, 0, count);
    }

    public byte[] toByteArray() {
        if (count != buf.length) {
            buf = super.toByteArray();
        }
        return buf;
    }
}