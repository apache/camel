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
package org.apache.camel.component.jasypt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class MainTest {

    @Test
    public void testMainShowOptions() {
        assertDoesNotThrow(() -> Main.main(new String[] {}));
    }

    @Test
    public void testMainEncrypt() {
        Main main = new Main();
        assertDoesNotThrow(() -> main.run("-c encrypt -p secret -i tiger".split(" ")));
    }

    @Test
    public void testMainDecrypt() {
        Main main = new Main();
        assertDoesNotThrow(() -> main.run("-c decrypt -p secret -i bsW9uV37gQ0QHFu7KO03Ww==".split(" ")));
    }

    @Test
    public void testUnknownOption() {
        Main main = new Main();
        assertDoesNotThrow(() -> main.run("-c encrypt -xxx foo".split(" ")));
    }

    @Test
    public void testMissingPassword() {
        Main main = new Main();
        assertDoesNotThrow(() -> main.run("-c encrypt -i tiger".split(" ")));
    }

    @Test
    public void testMissingInput() {
        Main main = new Main();
        assertDoesNotThrow(() -> main.run("-c encrypt -p secret".split(" ")));
    }

}
