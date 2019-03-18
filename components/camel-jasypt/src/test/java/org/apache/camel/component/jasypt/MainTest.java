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

import org.junit.Assert;
import org.junit.Test;

public class MainTest extends Assert {

    @Test
    public void testMainShowOptions() throws Exception {
        Main.main(new String[]{});
    }

    @Test
    public void testMainEncrypt() throws Exception {
        Main main = new Main();
        main.run("-c encrypt -p secret -i tiger".split(" "));
    }

    @Test
    public void testMainDecrypt() throws Exception {
        Main main = new Main();
        main.run("-c decrypt -p secret -i bsW9uV37gQ0QHFu7KO03Ww==".split(" "));
    }

    @Test
    public void testUnknownOption() throws Exception {
        Main main = new Main();
        main.run("-c encrypt -xxx foo".split(" "));
    }

    @Test
    public void testMissingPassword() throws Exception {
        Main main = new Main();
        main.run("-c encrypt -i tiger".split(" "));
    }

    @Test
    public void testMissingInput() throws Exception {
        Main main = new Main();
        main.run("-c encrypt -p secret".split(" "));
    }

}
