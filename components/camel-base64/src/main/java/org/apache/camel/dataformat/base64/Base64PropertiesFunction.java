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
package org.apache.camel.dataformat.base64;

import org.apache.camel.spi.PropertiesFunction;
import org.apache.commons.codec.binary.Base64;

@org.apache.camel.spi.annotations.PropertiesFunction("base64")
public class Base64PropertiesFunction implements PropertiesFunction {

    private final int lineLength = Base64.MIME_CHUNK_SIZE;
    private final byte[] lineSeparator = { '\r', '\n' };
    private final Base64 codec;

    public Base64PropertiesFunction() {
        this.codec = new Base64(lineLength, lineSeparator, true);
    }

    @Override
    public String getName() {
        return "base64";
    }

    @Override
    public boolean lookupFirst(String remainder) {
        return !remainder.startsWith("decode:");
    }

    @Override
    public String apply(String remainder) {
        if (remainder.startsWith("decode:")) {
            remainder = remainder.substring(7);
        }
        byte[] arr = codec.decode(remainder);
        return new String(arr);
    }
}
