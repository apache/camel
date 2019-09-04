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
package org.apache.camel.component.hbase.converter;

import org.apache.camel.Converter;
import org.apache.hadoop.hbase.util.Bytes;

@Converter(generateLoader = true)
public final class HBaseModelConverter {

    private HBaseModelConverter() {
        //Utility Class
    }

    @Converter
    public static byte[] booleanToBytes(Boolean bool) {
        return Bytes.toBytes(bool);
    }

    @Converter
    public static Boolean bytesToBoolean(byte[] bytes) {
        return Bytes.toBoolean(bytes);
    }

    @Converter
    public static byte[] shortToBytes(Short num) {
        return Bytes.toBytes(num);
    }

    @Converter
    public static Short bytesToShort(byte[] bytes) {
        return Bytes.toShort(bytes);
    }

    @Converter
    public static byte[] integerToBytes(Integer num) {
        return Bytes.toBytes(num);
    }

    @Converter
    public static Integer bytesToInteger(byte[] bytes) {
        return Bytes.toInt(bytes);
    }

    @Converter
    public static byte[] longToBytes(Long num) {
        return Bytes.toBytes(num);
    }

    @Converter
    public static Long bytesToLong(byte[] bytes) {
        return Bytes.toLong(bytes);
    }

    @Converter
    public static byte[] doubleToBytes(Double num) {
        return Bytes.toBytes(num);
    }

    @Converter
    public static Double bytesToDouble(byte[] bytes) {
        return Bytes.toDouble(bytes);
    }

    @Converter
    public static byte[] floatToBytes(Float num) {
        return Bytes.toBytes(num);
    }

    @Converter
    public static Float bytesToFloat(byte[] bytes) {
        return Bytes.toFloat(bytes);
    }
}
