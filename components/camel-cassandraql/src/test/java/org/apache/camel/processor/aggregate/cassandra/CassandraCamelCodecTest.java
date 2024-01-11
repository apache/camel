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
package org.apache.camel.processor.aggregate.cassandra;

import java.io.*;
import java.nio.ByteBuffer;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.malicious.example.Employee;

public class CassandraCamelCodecTest extends CamelTestSupport {

    CassandraCamelCodec codec;

    @Override
    protected void startCamelContext() throws Exception {
        super.startCamelContext();
        codec = new CassandraCamelCodec();
    }

    @Test
    public void shouldFailWithRejected() throws IOException, ClassNotFoundException {
        Employee emp = new Employee("Mickey", "Mouse");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);

        oos.writeObject(emp);

        oos.flush();
        oos.close();

        InputStream is = new ByteArrayInputStream(baos.toByteArray());
        InvalidClassException thrown = Assertions.assertThrows(InvalidClassException.class, () -> {
            codec.unmarshallExchange(context, ByteBuffer.wrap(is.readAllBytes()), "java.**;org.apache.camel.**;!*");
        });

        Assertions.assertEquals("filter status: REJECTED", thrown.getMessage());
    }
}
