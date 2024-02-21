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
package org.apache.camel.test.spring;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CamelSpringTestSupportTest {

    @Test
    public void testReplacement() throws IOException {
        String input = "<camel id='{{myCamelContext}}'>\n" +
                       "    <bean class='{{fooClass}}'/>\n" +
                       "</camel>\n";
        Resource io = new ByteArrayResource(input.getBytes(StandardCharsets.UTF_8));
        Map<String, String> props = new HashMap<>();
        props.put("myCamelContext", "camel-context-id");
        Resource tr = new CamelSpringTestSupport.TranslatedResource(io, props);
        byte[] buf = new byte[1024];
        int l = tr.getInputStream().read(buf);
        String output = new String(buf, 0, l, StandardCharsets.UTF_8);
        assertEquals("<camel id='camel-context-id'>\n" +
                     "    <bean class='{{fooClass}}'/>\n" +
                     "</camel>\n",
                output);
    }
}
