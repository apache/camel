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
package org.apache.camel.management;

import javax.management.ObjectName;

import junit.framework.TestCase;

public class ObjectNameEncoderTest extends TestCase {
    private static final String BEFOR_ENCODING = "test:test:hello*adsfad*";
    private static final String AFTER_ENCODING_1 = "test%3atest%3ahello%2aadsfad%2a";
    private static final String AFTER_ENCODING_2 = "test%3atest%3ahello*adsfad*";

    public void testEncoding() {
        assertEquals("Get the wrong endcoding result", ObjectNameEncoder.encode(BEFOR_ENCODING), AFTER_ENCODING_1);
        assertEquals("Get the wrong endcoding result with ignore Wildcards", ObjectNameEncoder.encode(BEFOR_ENCODING, true), AFTER_ENCODING_2);
    }

    public void testDecoding() {
        assertEquals("Get the wrong decoding result", ObjectNameEncoder.decode(AFTER_ENCODING_1), BEFOR_ENCODING);
        assertEquals("Get the wrong decoding result with ignore Wildcards", ObjectNameEncoder.decode(AFTER_ENCODING_2), BEFOR_ENCODING);
    }

}
