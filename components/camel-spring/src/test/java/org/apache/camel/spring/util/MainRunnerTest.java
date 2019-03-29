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
package org.apache.camel.spring.util;

import org.apache.camel.TestSupport;
import org.junit.Test;

public class MainRunnerTest extends TestSupport {

    @Test
    public void testMainRunner() throws Exception {
        MainRunner runner = new MainRunner();
        runner.setDelay(10);
        runner.setArgs(new String[]{"Hello", "World"});
        runner.setMain(MainRunnerTest.class);
        runner.afterPropertiesSet();
        runner.run();
    }

    public static void main(String[] args) {
        assertEquals("Hello", args[0]);
        assertEquals("World", args[1]);
    }

}
