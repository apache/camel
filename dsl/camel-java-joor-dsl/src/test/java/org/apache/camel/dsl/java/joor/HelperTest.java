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
package org.apache.camel.dsl.java.joor;

import java.io.FileInputStream;
import java.util.Collections;
import java.util.List;

import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HelperTest {

    @Test
    public void testImports() throws Exception {
        List<String> list = Helper.determineImports(
                IOHelper.loadText(new FileInputStream("src/test/java/org/apache/camel/dsl/java/joor/DummyRoute.java")));
        Collections.sort(list);
        Assertions.assertEquals(2, list.size());
        Assertions.assertEquals("org.apache.camel.CamelContext", list.get(0));
        Assertions.assertEquals("org.apache.camel.builder.RouteBuilder", list.get(1));
    }
}
