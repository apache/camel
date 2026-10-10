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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.ArrayList;
import java.util.List;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.markdown.MarkdownView;
import dev.tamboui.style.Hyperlink;
import dev.tamboui.style.Style;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinkTargetsTest {

    @Test
    void dropsControlCharactersFromText() {
        assertThat(LinkTargets.withoutControls("https://example.com/a\u0007b\u001bc\u009bd\u007fe"))
                .isEqualTo("https://example.com/abcde");
    }

    @Test
    void keepsPlainTextAsIs() {
        String url = "https://example.com/path?q=1;x=2#frag-é-世";
        assertThat(LinkTargets.withoutControls(url)).isSameAs(url);
    }

    @Test
    void tidiesTheUrlAndIdOfALink() {
        Hyperlink tidied = LinkTargets.tidy(Hyperlink.of("https://example.com/\u0007x", "id\u001b1"));

        assertThat(tidied.url()).isEqualTo("https://example.com/x");
        assertThat(tidied.id()).contains("id1");
    }

    @Test
    void keepsALinkWithoutControlCharacters() {
        Hyperlink link = Hyperlink.of("https://camel.apache.org", "doc");
        assertThat(LinkTargets.tidy(link)).isSameAs(link);
    }

    @Test
    void tidiesTheLinksOfABufferKeepingTheCells() {
        Buffer buffer = Buffer.empty(new Rect(0, 0, 4, 1));
        Style style = Style.EMPTY.bold().hyperlink("https://example.com/\u0007");
        buffer.set(1, 0, new Cell("x", style));

        LinkTargets.tidy(buffer);

        Cell cell = buffer.get(1, 0);
        assertThat(cell.symbol()).isEqualTo("x");
        assertThat(cell.style().hyperlink()).map(Hyperlink::url).contains("https://example.com/");
        assertThat(cell.style().addModifiers()).isEqualTo(style.addModifiers());
    }

    @Test
    void tidiesALinkOfMarkdownWithCharacterReferences() {
        // character references in a link destination become the characters themselves
        String markdown = "See [the docs](<https://example.com/&#7;&#27;[2J>) for details.";
        Buffer buffer = Buffer.empty(new Rect(0, 0, 40, 1));
        MarkdownView.builder().source(markdown).build().render(buffer.area(), buffer);

        LinkTargets.tidy(buffer);

        List<String> urls = new ArrayList<>();
        for (int x = 0; x < 40; x++) {
            buffer.get(x, 0).style().hyperlink().ifPresent(link -> urls.add(link.url()));
        }
        assertThat(urls).isNotEmpty().allMatch(url -> url.equals("https://example.com/[2J"));
    }
}
