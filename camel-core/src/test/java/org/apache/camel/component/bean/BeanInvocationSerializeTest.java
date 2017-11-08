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
package org.apache.camel.component.bean;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;

import org.apache.camel.TestSupport;

/**
 * @version 
 */
public class BeanInvocationSerializeTest extends TestSupport {

    public void testSerialize() throws Exception {
        Method method = getClass().getMethod("cheese", String.class, String.class);
        BeanInvocation invocation = new BeanInvocation(method, new Object[] {"a", "b"});
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(buffer);
        out.writeObject(invocation);
        out.close();

        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()));
        Object object = in.readObject();
        BeanInvocation actual = assertIsInstanceOf(BeanInvocation.class, object);
        log.debug("Received " + actual);
    }

    public void testSerializeCtr() throws Exception {
        Method method = getClass().getMethod("cheese", String.class, String.class);
        BeanInvocation invocation = new BeanInvocation();
        invocation.setArgs(new Object[] {"a", "b"});
        invocation.setMethod(method);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(buffer);
        out.writeObject(invocation);
        out.close();

        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()));
        Object object = in.readObject();
        BeanInvocation actual = assertIsInstanceOf(BeanInvocation.class, object);
        log.debug("Received " + actual);
    }

    public void cheese(String a, String b) {
        log.debug("Called with a: {} b: {}", a, b);
    }

}
