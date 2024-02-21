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
package org.apache.camel.maven.htmlxlsx.process;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TemplateRendererTest {

    private static final String EXPECTED = "<!--\n" +
                                           "\n" +
                                           "    Licensed to the Apache Software Foundation (ASF) under one or more\n" +
                                           "    contributor license agreements.  See the NOTICE file distributed with\n" +
                                           "    this work for additional information regarding copyright ownership.\n" +
                                           "    The ASF licenses this file to You under the Apache License, Version 2.0\n" +
                                           "    (the \"License\"); you may not use this file except in compliance with\n" +
                                           "    the License.  You may obtain a copy of the License at\n" +
                                           "\n" +
                                           "         http://www.apache.org/licenses/LICENSE-2.0\n" +
                                           "\n" +
                                           "    Unless required by applicable law or agreed to in writing, software\n" +
                                           "    distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                                           "    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                                           "    See the License for the specific language governing permissions and\n" +
                                           "    limitations under the License.\n" +
                                           "\n" +
                                           "-->\n" +
                                           "<!DOCTYPE html>\n" +
                                           "<html lang=\"en\">\n" +
                                           "\n" +
                                           "    <body>\n" +
                                           "        <h1>testRender</h1>\n" +
                                           "    </body>\n" +
                                           "</html>";

    @Test
    public void testTemplateRenderer() {

        // keep jacoco happy
        TemplateRenderer result = new TemplateRenderer();

        assertNotNull(result);
    }

    @Test
    public void testRender() {

        String result = TemplateRenderer.render("index", Map.of("testValue", "testRender"));

        assertEquals(EXPECTED, result);
    }

}
