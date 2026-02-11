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
package org.apache.camel.dsl.jbang.it;

import java.io.IOException;

import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import static org.junit.jupiter.api.condition.OS.WINDOWS;

@DisabledOnOs(WINDOWS)
public class ScriptingWithPipesITCase extends JBangTestSupport {
    @Test
    public void testPipeScript() throws IOException {
        newFileInDataFolder("UpperCase.java",
                "///usr/bin/env jbang --quiet camel@apache/camel script \"$0\" \"$@\" ; exit $?\n" +
                                              "import org.apache.camel.builder.RouteBuilder;\n" +
                                              "\n" +
                                              "public class UpperCase extends RouteBuilder {\n" +
                                              "    @Override\n" +
                                              "    public void configure() {\n" +
                                              "        from(\"stream:in\")\n" +
                                              "                .setBody()\n" +
                                              "                .simple(\"${body.toUpperCase()}\")\n" +
                                              "                .to(\"stream:out\");\n" +
                                              "    }\n" +
                                              "}");
        execInContainer(String.format("chmod +x %s/UpperCase.java", mountPoint()));
        execInContainer(String.format("echo \"hello camel\" | %s/UpperCase.java > %s/hello.txt", mountPoint(), mountPoint()));
        assertFileInDataFolderContains("hello.txt", "HELLO CAMEL");
    }
}
