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
package org.apache.camel.component.properties;

import java.io.FileOutputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;

/**
 * @version 
 */
public class PropertiesComponentLoadPropertiesFromFileTrimValuesTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/space");
        createDirectory("target/space");
        super.setUp();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        // create space.properties file
        FileOutputStream fos = new FileOutputStream("target/space/space.properties");
        String cool = "cool.leading= Leading space" + LS + "cool.trailing=Trailing space " + LS + "cool.both= Both leading and trailing space ";
        fos.write(cool.getBytes());
        fos.write(LS.getBytes());

        String space = "space.leading=   \\r\\n" + LS + "space.trailing=\\t   " + LS + "space.both=  \\r   \\t  \\n   ";
        fos.write(space.getBytes());
        fos.write(LS.getBytes());

        String mixed = "mixed.leading=   Leading space\\r\\n" + LS + "mixed.trailing=Trailing space\\t   " + LS + "mixed.both=  Both leading and trailing space\\r   \\t  \\n   ";
        fos.write(mixed.getBytes());
        fos.write(LS.getBytes());

        String empty = "empty.line=                               ";
        fos.write(empty.getBytes());

        fos.close();

        PropertiesComponent pc = new PropertiesComponent();
        pc.setLocation("file:target/space/space.properties");
        context.addComponent("properties", pc);

        return context;
    }

    public void testMustTrimValues() throws Exception {
        assertEquals("Leading space", context.resolvePropertyPlaceholders("{{cool.leading}}"));
        assertEquals("Trailing space", context.resolvePropertyPlaceholders("{{cool.trailing}}"));
        assertEquals("Both leading and trailing space", context.resolvePropertyPlaceholders("{{cool.both}}"));

        assertEquals("\r\n", context.resolvePropertyPlaceholders("{{space.leading}}"));
        assertEquals("\t", context.resolvePropertyPlaceholders("{{space.trailing}}"));
        assertEquals("\r   \t  \n", context.resolvePropertyPlaceholders("{{space.both}}"));

        assertEquals("Leading space\r\n", context.resolvePropertyPlaceholders("{{mixed.leading}}"));
        assertEquals("Trailing space\t", context.resolvePropertyPlaceholders("{{mixed.trailing}}"));
        assertEquals("Both leading and trailing space\r   \t  \n", context.resolvePropertyPlaceholders("{{mixed.both}}"));

        assertEquals("", context.resolvePropertyPlaceholders("{{empty.line}}"));
    }

}