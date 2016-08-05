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
package org.apache.camel.itest.springboot.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Provide basic serialization utils.
 * Cannot rely on common available libraries to not influence the test outcome.
 */
public final class SerializationUtils {

    private SerializationUtils() {
    }

    public static byte[] marshal(Object o) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ObjectOutputStream oout = new ObjectOutputStream(out)) {
            oout.writeObject(o);
            oout.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Unable to marshal the bean " + o, e);
        }
    }

    public static Object unmarshal(byte[] content) {
        if (content == null) {
            return null;
        }

        try (ByteArrayInputStream in = new ByteArrayInputStream(content); ObjectInputStream oin = new ObjectInputStream(in)) {
            Object bean = oin.readObject();
            return bean;

        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Unable to unmarshal the bean", e);
        }
    }

    public static Throwable transferable(Throwable ex) {
        try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
            ex.printStackTrace(pw);
            pw.flush();

            return new Exception("Error while executing tests. Wrapped exception is: " + sw.toString());
        } catch (IOException e) {
            return new RuntimeException("Error while cleaning up the exception. Message=" + e.getMessage());
        }
    }

}
