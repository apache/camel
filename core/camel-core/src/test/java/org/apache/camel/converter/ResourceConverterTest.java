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
package org.apache.camel.converter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.spi.Resource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResourceConverterTest extends ContextTestSupport {

    @Test
    public void testMem() {
        Resource res = context.getTypeConverter().convertTo(Resource.class, "mem:foo");
        String out = context.getTypeConverter().convertTo(String.class, res);
        assertEquals("foo", out);
    }

    @Test
    public void testBase64() {
        String b64 = Base64.getEncoder().encodeToString("Hello Camel".getBytes(StandardCharsets.UTF_8));
        Resource res = context.getTypeConverter().convertTo(Resource.class, "base64:" + b64);
        String out = context.getTypeConverter().convertTo(String.class, res);
        assertEquals("Hello Camel", out);
    }

    @Test
    public void testClasspath() {
        Resource res = context.getTypeConverter().convertTo(Resource.class, "classpath:myxpath.txt");
        String out = context.getTypeConverter().convertTo(String.class, res);
        assertEquals("/person/name/text()", out);
    }

    @Test
    public void testFile() {
        Resource res = context.getTypeConverter().convertTo(Resource.class, "file:src/test/resources/mysimple.txt");
        String out = context.getTypeConverter().convertTo(String.class, res);
        assertEquals("The name is ${body}", out);
    }

    @Test
    public void testRef() {
        context.getRegistry().bind("foo", "Hello Foo");
        Resource res = context.getTypeConverter().convertTo(Resource.class, "ref:foo");
        String out = context.getTypeConverter().convertTo(String.class, res);
        assertEquals("Hello Foo", out);
    }

}
