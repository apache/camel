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
package org.apache.camel.xml.in;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.xml.io.XmlPullParserException;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class ModelParserTest {

    @Test
    public void test() throws Exception {
        Path dir = getResourceFolder();
        Files.list(dir).sorted().filter(Files::isRegularFile).forEach(path -> {
            try {
                if (path.getFileName().toString().equals("barRest.xml")
                        || path.getFileName().toString().equals("simpleRest.xml")
                        || path.getFileName().toString().equals("simpleRestToD.xml")) {
                    RestsDefinition rests = new ModelParser(
                            Files.newInputStream(path), "http://camel.apache.org/schema/spring")
                            .parseRestsDefinition();
                    System.out.println("Parsed " + path + " successfully");
                    assertNotNull(rests);
                } else {
                    RoutesDefinition routes = new ModelParser(
                            Files.newInputStream(path), "http://camel.apache.org/schema/spring")
                            .parseRoutesDefinition();
                    System.out.println("Parsed " + path + " successfully");
                    assertNotNull(routes);
                }
            } catch (IOException|XmlPullParserException e) {
                throw new RuntimeException("Error parsing: " + path, e);
            }
        });

        RoutesDefinition routes = new ModelParser(new StringReader(
                "<routes>" +
                        "<route id='foo'>" +
                            "<from uri='my:bar'/>" +
                            "<to uri='mock:res'/>" +
                        "</route>" +
                "</routes>"
        )).parseRoutesDefinition();
        assertNotNull(routes);
    }

    private Path getResourceFolder() {
        String url = getClass().getClassLoader().getResource("barInterceptorRoute.xml").toString();
        if (url.startsWith("file:")) {
            url = url.substring("file:".length(), url.indexOf("barInterceptorRoute.xml"));
        } else if (url.startsWith("jar:file:")) {
            url = url.substring("jar:file:".length(), url.indexOf("!"));
        }
        return Paths.get(url);
    }
}
