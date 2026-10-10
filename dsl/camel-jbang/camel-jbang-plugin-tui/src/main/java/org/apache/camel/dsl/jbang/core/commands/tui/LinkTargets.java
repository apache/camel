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

import java.util.Optional;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Hyperlink;
import dev.tamboui.style.Style;

/**
 * Tidies the link targets of a rendered frame. A link target (the URL and the id of a link in the text, such as a
 * markdown link in an AI answer or a document) is plain text, so control characters in it are dropped before the frame
 * is drawn.
 */
final class LinkTargets {

    private LinkTargets() {
    }

    /**
     * Drops the control characters from the URL and the id of every link in the buffer.
     */
    static void tidy(Buffer buffer) {
        Rect area = buffer.area();
        for (int y = area.top(); y < area.bottom(); y++) {
            for (int x = area.left(); x < area.right(); x++) {
                Cell cell = buffer.get(x, y);
                Optional<Hyperlink> link = cell.style().hyperlink();
                if (link.isPresent()) {
                    Hyperlink tidied = tidy(link.get());
                    if (tidied != link.get()) {
                        buffer.set(x, y, cell.style(withLink(cell.style(), tidied)));
                    }
                }
            }
        }
    }

    /** The link without control characters in its URL and id; the same instance when it has none. */
    static Hyperlink tidy(Hyperlink link) {
        String url = withoutControls(link.url());
        String id = link.id().map(LinkTargets::withoutControls).orElse(null);
        if (url.equals(link.url()) && (id == null || id.equals(link.id().get()))) {
            return link;
        }
        return id != null ? Hyperlink.of(url, id) : Hyperlink.of(url);
    }

    private static Style withLink(Style style, Hyperlink link) {
        return link.id().isPresent() ? style.hyperlink(link.url(), link.id().get()) : style.hyperlink(link.url());
    }

    /** The text without C0 controls (U+0000 to U+001F), DEL and C1 controls (U+0080 to U+009F). */
    static String withoutControls(String text) {
        StringBuilder sb = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean control = c < 0x20 || (c >= 0x7F && c <= 0x9F);
            if (control && sb == null) {
                sb = new StringBuilder(text.length()).append(text, 0, i);
            } else if (!control && sb != null) {
                sb.append(c);
            }
        }
        return sb != null ? sb.toString() : text;
    }
}
