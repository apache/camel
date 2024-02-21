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
package org.apache.camel.util.json;

import java.util.Date;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JSonerTest {

    @Test
    public void testTrySerialize() throws Exception {
        String s = Jsoner.trySerialize("Hello World");
        Assertions.assertEquals("\"Hello World\"", s);

        s = Jsoner.trySerialize(123);
        Assertions.assertEquals("123", s);

        s = Jsoner.trySerialize(true);
        Assertions.assertEquals("true", s);

        s = Jsoner.trySerialize(new Date());
        Assertions.assertNull(s);

    }
}
