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
package org.apache.camel.processor.aggregate.jdbc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultExchangeHolder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClassLoadingAwareObjectInputStreamTest {

    @Test
    public void deserialize() throws IOException, ClassNotFoundException {
        CamelContext context = new DefaultCamelContext();

        final DefaultExchange exchange = new DefaultExchange(context);

        final List<MyObject> objects = new ArrayList<>();
        final MyObject o = new MyObject("leb", "hello".getBytes());
        objects.add(o);

        exchange.getIn().setBody(objects);
        final DefaultExchangeHolder deh = DefaultExchangeHolder.marshal(exchange);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(deh);
        oos.flush();
        final byte[] serialized = baos.toByteArray();

        final ObjectInputStream bis = new ClassLoadingAwareObjectInputStream(context, new ByteArrayInputStream(serialized));
        final DefaultExchangeHolder deserialized = (DefaultExchangeHolder) bis.readObject();

        final DefaultExchange exchange2 = new DefaultExchange(context);
        DefaultExchangeHolder.unmarshal(exchange2, deserialized);

        List<MyObject> receivedObjects = exchange2.getIn().getBody(List.class);
        assertEquals(1, receivedObjects.size());
        assertEquals(o, receivedObjects.get(0));
    }

}


class MyObject implements Serializable {
    final String name;
    final byte[] content;

    MyObject(String name, byte[] content) {
        this.name = name;
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

        if (name != null ? !name.equals(myObject.name) : myObject.name != null) {
            return false;
        }
        return Arrays.equals(content, myObject.content);
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }
}
