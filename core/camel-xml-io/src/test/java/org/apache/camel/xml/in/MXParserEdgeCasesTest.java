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

import org.apache.camel.xml.io.MXParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MXParserEdgeCasesTest {

    private static String textOf(String xml) throws Exception {
        MXParser parser = new BaseParser(new StringReader(xml)).parser;
        assertThat(parser.next()).isEqualTo(MXParser.START_TAG);
        assertThat(parser.next()).isEqualTo(MXParser.TEXT);
        return parser.getText();
    }

    @Test
    public void testTwoCDataFollowedByText() throws Exception {
        assertThat(textOf("<a><![CDATA[x]]><![CDATA[y]]>z</a>")).isEqualTo("xyz");
        // the idiom to have ]]> in cdata, and a new line before the end tag
        assertThat(textOf("<a><![CDATA[a]]]]><![CDATA[>b]]>\n</a>")).isEqualTo("a]]>b\n");
        assertThat(textOf("<a><![CDATA[x]]><![CDATA[y]]>&amp;z</a>")).isEqualTo("xy&z");
        assertThat(textOf("<a><![CDATA[x]]><![CDATA[y]]><!-- c -->z</a>")).isEqualTo("xyz");
    }

    @Test
    public void testCharacterReferenceAboveBmp() throws Exception {
        assertThat(textOf("<a>&#x1F600;</a>")).isEqualTo("😀");
        assertThat(textOf("<a>&#128512;</a>")).isEqualTo("😀");

        MXParser parser = new BaseParser(new StringReader("<a b=\"&#x1F600;\"/>")).parser;
        assertThat(parser.next()).isEqualTo(MXParser.START_TAG);
        assertThat(parser.getAttributeValue(0)).isEqualTo("😀");
    }
}
