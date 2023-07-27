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
package org.apache.camel.util.xml.pretty;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class XmlPrettyPrinterTest {

    @Test
    public void testPrettyPrint() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root><tag><nested>hello</nested></tag></root>";
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                          + "<root>\n"
                          + "  <tag>\n"
                          + "    <nested>\n"
                          + "      hello\n"
                          + "    </nested>\n"
                          + "  </tag>\n"
                          + "</root>";
        String pretty = XmlPrettyPrinter.pettyPrint(xml, 2, true);
        Assertions.assertEquals(expected, pretty);
    }

    @Test
    public void testPrettyPrintNoDecl() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root><tag><nested>hello</nested></tag></root>";
        String expected = "<root>\n"
                          + "  <tag>\n"
                          + "    <nested>\n"
                          + "      hello\n"
                          + "    </nested>\n"
                          + "  </tag>\n"
                          + "</root>";
        String pretty = XmlPrettyPrinter.pettyPrint(xml, 2, false);
        Assertions.assertEquals(expected, pretty);
    }

}
