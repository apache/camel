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
package org.apache.camel.component.hbase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.hbase.processor.idempotent.HBaseIdempotentRepository;
import org.apache.camel.util.IOHelper;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HBaseHelper {

    private static final Logger LOG = LoggerFactory.getLogger(HBaseIdempotentRepository.class);
    private static final Map<String, byte[]> NAMES = new HashMap<>();

    private HBaseHelper() {
        //Utility Class
    }

    public static byte[] getHBaseFieldAsBytes(String n) {
        byte[] name = null;
        name = NAMES.get(n);
        if (name == null && n != null) {
            name = n.getBytes();
            NAMES.put(n, name);
        }
        return name;
    }

    public static byte[] toBytes(Object obj) {
        if (obj instanceof String) {
            return Bytes.toBytes((String) obj);
        } else if (obj instanceof byte[]) {
            return (byte[]) obj;
        } else if (obj instanceof Byte) {
            return Bytes.toBytes((Byte) obj);
        } else if (obj instanceof Short) {
            return Bytes.toBytes((Short) obj);
        } else if (obj instanceof Integer) {
            return Bytes.toBytes((Integer) obj);
        } else if (obj instanceof Long) {
            return Bytes.toBytes((Long) obj);
        } else if (obj instanceof Double) {
            return Bytes.toBytes((Double) obj);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = null;
            try {
                oos = new ObjectOutputStream(baos);
                oos.writeObject(obj);
                return baos.toByteArray();
            } catch (IOException e) {
                LOG.warn("Error while serializing object. Null will be used.", e);
                return null;
            } finally {
                IOHelper.close(oos);
                IOHelper.close(baos);
            }
        }
    }

}
