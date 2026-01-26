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

    private static final String EXPECTED = """
            <!--

                Licensed to the Apache Software Foundation (ASF) under one or more
                contributor license agreements.  See the NOTICE file distributed with
                this work for additional information regarding copyright ownership.
                The ASF licenses this file to You under the Apache License, Version 2.0
                (the "License"); you may not use this file except in compliance with
                the License.  You may obtain a copy of the License at

                     http://www.apache.org/licenses/LICENSE-2.0

                Unless required by applicable law or agreed to in writing, software
                distributed under the License is distributed on an "AS IS" BASIS,
                WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                See the License for the specific language governing permissions and
                limitations under the License.

            -->
            <!DOCTYPE html>
            <html lang="en">

                <body>
                    <h1>testRender</h1>
                </body>
            </html>""";

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
