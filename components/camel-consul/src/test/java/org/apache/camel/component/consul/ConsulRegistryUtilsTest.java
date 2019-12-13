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
package org.apache.camel.component.consul;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.component.consul.ConsulRegistry.ConsulRegistryUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class ConsulRegistryUtilsTest {

    @Test
    public void encodeDecode() {
        final List<String> src = Arrays.asList("one", "\u0434\u0432\u0430", "t\u0159i");
        final byte[] serialized = ConsulRegistryUtils.serialize((Serializable)src);
        assertEquals(src, ConsulRegistryUtils.deserialize(serialized));
        final String encoded = ConsulRegistryUtils.encodeBase64(serialized);
        assertEquals("rO0ABXNyABpqYXZhLnV0aWwuQXJyYXlzJEFycmF5TGlzdNmkPL7NiAbSAgABWwABYXQAE1tMamF2YS9sYW5nL09iamVjdDt4"
                     + "cHVyABNbTGphdmEubGFuZy5TdHJpbmc7rdJW5+kde0cCAAB4cAAAAAN0AANvbmV0AAbQtNCy0LB0AAR0xZlp", encoded);
        final byte[] decoded = ConsulRegistryUtils.decodeBase64(encoded);
        assertArrayEquals(new byte[] {-84, -19, 0, 5, 115, 114, 0, 26, 106, 97, 118, 97, 46, 117, 116, 105, 108, 46, 65, 114, 114, 97, 121, 115, 36, 65, 114, 114, 97, 121, 76, 105,
                                      115, 116, -39, -92, 60, -66, -51, -120, 6, -46, 2, 0, 1, 91, 0, 1, 97, 116, 0, 19, 91, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 79,
                                      98, 106, 101, 99, 116, 59, 120, 112, 117, 114, 0, 19, 91, 76, 106, 97, 118, 97, 46, 108, 97, 110, 103, 46, 83, 116, 114, 105, 110, 103, 59,
                                      -83, -46, 86, -25, -23, 29, 123, 71, 2, 0, 0, 120, 112, 0, 0, 0, 3, 116, 0, 3, 111, 110, 101, 116, 0, 6, -48, -76, -48, -78, -48, -80, 116, 0,
                                      4, 116, -59, -103, 105},
                          decoded);
        assertEquals(src, ConsulRegistryUtils.deserialize(decoded));
    }

}
