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

/**
 * Lets the user pick which part of an AI answer to copy when it contains several code blocks: one row per block (with
 * language, size and a preview of its first line) and the whole answer as the last row.
 */
final class AiCopyPopup extends ChoicePopup<AiCopyPopup.Choice> {

    /** What to copy: {@code text} is the clipboard content, {@code what} names it in the notification. */
    record Choice(String label, String what, String text) {
    }

    void open(String answer, List<AiCodeBlocks.CodeBlock> blocks) {
        List<Choice> list = new ArrayList<>();
        int n = 1;
        for (AiCodeBlocks.CodeBlock block : blocks) {
            String preview = block.preview(40);
            String label = n + "  " + block.label() + (preview.isEmpty() ? "" : "  " + preview);
            list.add(new Choice(label, block.label(), block.code()));
            n++;
        }
        list.add(new Choice(n + "  Whole response", "response", answer));
        show(list);
    }

    @Override
    protected String title() {
        return " Copy ";
    }

    @Override
    protected String label(Choice choice) {
        return choice.label();
    }

    @Override
    protected String confirmLabel() {
        return "copy";
    }

    @Override
    protected boolean pickByNumber() {
        return true;
    }
}
