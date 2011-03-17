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

import java.io.File;
import java.io.FileOutputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;

/**
 * @version 
 */
public class PropertiesComponentLoadPropertiesFromFileTrimValuesTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        // create space.properties file
        deleteDirectory("target/space");
        createDirectory("target/space");

        File file = new File("target/space/space.properties");
        file.createNewFile();
        FileOutputStream fos = new FileOutputStream(file);
        fos.write("cool.leading= Leading space\ncool.trailing=Trailing space \ncool.both= Both leading and trailing space ".getBytes());
        fos.close();

        PropertiesComponent pc = new PropertiesComponent();
        pc.setCamelContext(context);
        pc.setLocations(new String[]{"file:target/space/space.properties"});
        context.addComponent("properties", pc);

        return context;
    }

    public void testMustTrimValues() throws Exception {
        assertEquals("Leading space", context.resolvePropertyPlaceholders("{{cool.leading}}"));
        assertEquals("Trailing space", context.resolvePropertyPlaceholders("{{cool.trailing}}"));
        assertEquals("Both leading and trailing space", context.resolvePropertyPlaceholders("{{cool.both}}"));
    }

}