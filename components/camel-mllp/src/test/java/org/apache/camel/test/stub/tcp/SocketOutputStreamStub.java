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
package org.apache.camel.test.stub.tcp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class SocketOutputStreamStub extends OutputStream {
    public boolean failOnWrite;
    public boolean failOnWriteArray;
    public Byte writeFailOn;
    public byte[] writeArrayFailOn;
    ByteArrayOutputStream fakeOutputStream = new ByteArrayOutputStream();

    @Override
    public void write(int b) throws IOException {
        if (failOnWrite) {
            throw new IOException("Faking write failure");
        } else if (writeFailOn != null && writeFailOn == b) {
            throw new IOException("Faking write failure");
        }

        if (fakeOutputStream == null) {
            fakeOutputStream = new ByteArrayOutputStream();
        }

        fakeOutputStream.write(b);
    }

    @Override
    public void write(byte[] array, int off, int len) throws IOException {
        if (failOnWriteArray) {
            throw new IOException("Faking write array failure");
        }

        if (writeArrayFailOn != null) {
            if (writeArrayFailOn == array) {
                throw new IOException("Faking write array failure");
            }
            for (int i = 0; i < Math.min(len, writeArrayFailOn.length); ++i) {
                if (array[off + i] != writeArrayFailOn[i]) {
                    super.write(array, off, len);
                    return;
                }
            }
            throw new IOException("Faking write array failure");
        } else {
            super.write(array, off, len);
        }
    }

    public byte[] getPayload() {
        if (fakeOutputStream != null) {
            return fakeOutputStream.toByteArray();
        }

        return null;
    }

}
