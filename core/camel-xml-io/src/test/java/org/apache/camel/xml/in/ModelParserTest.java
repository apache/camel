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

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class ModelParserTest {

    public static final String NAMESPACE = "http://camel.apache.org/schema/spring";
    private static final List<String> REST_XMLS = Arrays.asList("barRest.xml", "simpleRest.xml", "simpleRestToD.xml");

    @Test
    public void testFiles() throws Exception {
        Path dir = getResourceFolder();
        List<Path> files = Files.list(dir).sorted().filter(Files::isRegularFile).collect(Collectors.toList());
        for (Path path : files) {
            ModelParser parser = new ModelParser(Files.newInputStream(path), NAMESPACE);
            boolean isRest = REST_XMLS.contains(path.getFileName().toString());
            if (isRest) {
                RestsDefinition rests = parser.parseRestsDefinition();
                assertNotNull(rests);
            } else {
                RoutesDefinition routes = parser.parseRoutesDefinition();
                assertNotNull(routes);
            }
        }
    }

    @Test
    public void testSimpleString() throws Exception {
        RoutesDefinition routes = new ModelParser(new StringReader("<routes>" + "  <route id='foo'>" + "    <from uri='my:bar'/>" + "    <to uri='mock:res'/>" + "  </route>"
                                                                   + "</routes>")).parseRoutesDefinition();
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
