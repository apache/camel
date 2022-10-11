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

package org.apache.camel.resume;

import java.io.File;
import java.nio.ByteBuffer;

public interface Deserializable {

    default Object deserializeObject(ByteBuffer buffer) {
        buffer.clear();

        int dataType = buffer.getInt();
        switch (dataType) {
            case Serializable.TYPE_INTEGER: {
                return buffer.getInt();
            }
            case Serializable.TYPE_LONG: {
                return buffer.getLong();
            }
            case Serializable.TYPE_STRING: {
                int remaining = buffer.remaining();
                byte[] tmp = new byte[remaining];
                buffer.get(tmp);

                return new String(tmp);
            }
            case Serializable.TYPE_FILE: {
                int remaining = buffer.remaining();
                byte[] tmp = new byte[remaining];
                buffer.get(tmp);

                return new File(new String(tmp));
            }
            default: {
                return null;
            }
        }
    }

    default Object deserializeKey(ByteBuffer keyBuffer) {
        return deserializeObject(keyBuffer);
    }

    default Object deserializeValue(ByteBuffer valueBuffer) {
        return deserializeObject(valueBuffer);
    }

    boolean deserialize(ByteBuffer keyBuffer, ByteBuffer valueBuffer);
}
