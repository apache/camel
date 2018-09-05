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

package org.apache.camel.test.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PayloadBuilder {
    ByteArrayOutputStream builderStream = new ByteArrayOutputStream();

    public PayloadBuilder() {
    }

    public PayloadBuilder(byte b) throws IOException {
        this.append(b);
    }

    public PayloadBuilder(byte[] bytes) throws IOException {
        this.append(bytes);
    }

    public PayloadBuilder(char... chars) throws IOException {
        this.append(chars);
    }

    public PayloadBuilder(String... strings) throws IOException {
        this.append(strings);
    }

    public static byte[] build(byte b) {
        try {
            return new PayloadBuilder(b).build();
        } catch (IOException e) {
            throw new RuntimeException("PayloadBuilder.build(byte) failure", e);
        }
    }

    public static byte[] build(byte b, byte... bytes) {
        try {
            return new PayloadBuilder(b).append(bytes).build();
        } catch (IOException e) {
            throw new RuntimeException("PayloadBuilder.build(byte) failure", e);
        }
    }

    public static byte[] build(byte[] bytes) {
        try {
            return new PayloadBuilder(bytes).build();
        } catch (IOException e) {
            throw new RuntimeException("PayloadBuilder.build(byte[]) failure", e);
        }
    }

    public static byte[] build(char c) {
        try {
            return new PayloadBuilder(c).build();
        } catch (IOException e) {
            throw new RuntimeException("PayloadBuilder.build(char...) failure", e);
        }
    }

    public static byte[] build(char c, char... chars) {
        try {
            return new PayloadBuilder(c).append(chars).build();
        } catch (IOException e) {
            throw new RuntimeException("PayloadBuilder.build(char...) failure", e);
        }
    }

    public static byte[] build(char[] chars) {
        try {
            return new PayloadBuilder(chars).build();
        } catch (IOException e) {
            throw new RuntimeException("PayloadBuilder.build(char...) failure", e);
        }
    }

    public static byte[] build(String s) {
        try {
            return new PayloadBuilder(s).build();
        } catch (IOException e) {
            throw new RuntimeException("PayloadBuilder.build(String) failure", e);
        }
    }

    public static byte[] build(String[] strings) {
        try {
            return new PayloadBuilder(strings).build();
        } catch (IOException e) {
            throw new RuntimeException("PayloadBuilder.build(String[]) failure", e);
        }
    }

    public static byte[] build(char start, String s) {
        try {
            return new PayloadBuilder(start)
                .append(s)
                .build();
        } catch (IOException e) {
            throw new RuntimeException("PayloadBuilder.build(String) failure", e);
        }
    }

    public static byte[] build(char start, String s, char... end) {
        try {
            return new PayloadBuilder(start)
                .append(s)
                .append(end)
                .build();
        } catch (IOException e) {
            throw new RuntimeException("PayloadBuilder.build(char, String, char...) failure", e);
        }
    }

    public static byte[] build(char start, byte[] bytes, char... end) {
        try {
            return new PayloadBuilder(start)
                .append(bytes)
                .append(end).build();
        } catch (IOException e) {
            throw new RuntimeException("PayloadBuilder.build(char, byte[], char...) failure", e);
        }
    }

    public static byte[] build(String s, char... end) {
        try {
            return new PayloadBuilder(s)
                .append(end).build();
        } catch (IOException e) {
            throw new RuntimeException("PayloadBuilder.build(String, char...) failure", e);
        }
    }

    public static byte[] build(byte[] bytes, char... end) {
        try {
            return new PayloadBuilder(bytes)
                .append(end).build();
        } catch (IOException e) {
            throw new RuntimeException("PayloadBuilder.build(byte[], char...) failure", e);
        }
    }

    public static byte[] build(byte[] bytes, String s) {
        try {
            return new PayloadBuilder(bytes)
                .append(s).build();
        } catch (IOException e) {
            throw new RuntimeException("PayloadBuilder.build(byte[], String) failure", e);
        }
    }

    public PayloadBuilder append(byte b) throws IOException {
        builderStream.write(b);

        return this;
    }

    public PayloadBuilder append(byte[] bytes) throws IOException {
        builderStream.write(bytes);

        return this;
    }

    public PayloadBuilder append(char... chars) throws IOException {
        if (chars != null) {
            for (char c : chars) {
                builderStream.write(c);
            }
        }

        return this;
    }

    public PayloadBuilder append(String... strings) throws IOException {
        if (strings != null) {
            for (String s : strings) {
                builderStream.write(s.getBytes());
            }
        }

        return this;
    }

    public PayloadBuilder append(byte[] payload, int startPosition, int length) throws IOException {
        builderStream.write(payload, startPosition, length);

        return this;
    }

    public byte[] build() {
        byte[] answer = builderStream.toByteArray();

        builderStream.reset();

        return answer;
    }
}
