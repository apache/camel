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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.dsl.jbang.core.common.SemanticImagePreview;

/** Decode off the render thread; draw only bounded pixels from the selected captured input. */
final class SemanticImageView {
    private final Executor executor;
    private Object selected;
    private CompletableFuture<List<SemanticImagePreview>> pending;

    SemanticImageView(Executor executor) {
        this.executor = executor;
    }

    void reset() {
        if (pending != null) {
            pending.cancel(true);
        }
        selected = null;
        pending = null;
    }

    List<Line> lines(Object input, int width) {
        if (selected != input) {
            reset();
            selected = input;
            pending = CompletableFuture.supplyAsync(() -> SemanticImagePreview.prepare(input), executor)
                    .exceptionally(failure -> List.of(new SemanticImagePreview("Image preview unavailable", List.of())));
        }
        if (pending == null || !pending.isDone()) {
            return List.of(Line.from("Preparing image preview…"));
        }
        List<Line> result = new ArrayList<>();
        for (var preview : pending.getNow(List.of())) {
            result.add(Line.from(Span.styled(preview.description(), Theme.label())));
            result.addAll(pixels(preview, width));
        }
        return result;
    }

    private static List<Line> pixels(SemanticImagePreview preview, int availableWidth) {
        List<List<Integer>> pixels = preview.pixels();
        if (pixels.isEmpty() || availableWidth <= 0) {
            return List.of();
        }
        int sourceWidth = pixels.get(0).size();
        int width = Math.min(sourceWidth, availableWidth);
        int height = Math.max(1, pixels.size() * width / sourceWidth);
        List<Line> lines = new ArrayList<>();
        for (int y = 0; y < height; y += 2) {
            List<Span> spans = new ArrayList<>();
            for (int x = 0; x < width; x++) {
                int sourceX = x * sourceWidth / width;
                int top = pixels.get(y * pixels.size() / height).get(sourceX);
                int bottom = pixels.get(Math.min(y + 1, height - 1) * pixels.size() / height).get(sourceX);
                spans.add(Span.styled("▀", Style.EMPTY.fg(color(top)).bg(color(bottom))));
            }
            lines.add(Line.from(spans));
        }
        return lines;
    }

    private static Color color(int rgb) {
        return Color.rgb(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255);
    }
}
