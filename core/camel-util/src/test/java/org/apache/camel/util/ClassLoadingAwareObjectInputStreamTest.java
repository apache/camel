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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClassLoadingAwareObjectInputStreamTest {

    @Test
    public void deserialize() throws IOException, ClassNotFoundException {
        final MyObject myObject = new MyObject("Test content");
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(myObject);
        oos.flush();
        final byte[] serialized = baos.toByteArray();

        final ObjectInputStream bis = new ClassLoadingAwareObjectInputStream(new ByteArrayInputStream(serialized));
        final MyObject deserialized = (MyObject) bis.readObject();

        assertEquals(myObject, deserialized);
    }
}

class MyObject implements Serializable {
    final String content;

    MyObject(String content) {
        this.content = content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MyObject myObject = (MyObject) o;
        return Objects.equals(content, myObject.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content);
    }
}
