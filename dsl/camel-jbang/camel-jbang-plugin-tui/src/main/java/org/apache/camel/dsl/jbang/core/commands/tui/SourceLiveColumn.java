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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import dev.tamboui.style.Style;
import dev.tamboui.text.Span;

/**
 * The live run data of the running integration in the source view ({@link SourceViewer}), a column at the end of the
 * line numbers: how many exchanges the processors on a line handled, how many failed, and their mean time (a heat map
 * of the source while it runs). A line that failed is what Shift+F8 asks the AI to fix when it has no problem.
 */
class SourceLiveColumn {

    /**
     * What a line of a running integration's source did: the exchanges of the processors on it, the failed ones and the
     * mean processing time.
     */
    record LiveLine(long total, long failed, long meanMillis) {
    }

    /** The live run data of a file by its path: per 0-based line, what the processors on it did. */
    @FunctionalInterface
    interface LiveRunData {
        Map<Integer, LiveLine> lines(String filePath);
    }

    /** The last runtime failure of a line of a file, by its path and 0-based line: the exception, or null. */
    @FunctionalInterface
    interface LineFailures {
        String lastFailure(String filePath, int line);
    }

    private final Supplier<String> loadedFile;
    private final BooleanSupplier dirty;
    private LiveRunData liveRunData;
    private LineFailures lineFailures;
    private Map<Integer, LiveLine> liveLines = Collections.emptyMap();
    private long liveLinesTime;
    private int liveTotalWidth;
    private int liveFailedWidth;
    private int liveMeanWidth;
    private String liveWidthsFile;

    /**
     * @param loadedFile the path of the file shown, whose live run data is read; null when none is
     * @param dirty      whether the file is edited and not saved, so the live run data is not of what is shown
     */
    SourceLiveColumn(Supplier<String> loadedFile, BooleanSupplier dirty) {
        this.loadedFile = loadedFile;
        this.dirty = dirty;
    }

    /** The path of the file shown, whose live run data is read; null when none is. */
    String loadedFile() {
        return loadedFile.get();
    }

    /**
     * The live run data of the running integration, shown at the end of the lines of its source: how many exchanges the
     * processors on a line handled, how many failed, and their mean time (a heat map of the source while it runs).
     */
    void setLiveRunData(LiveRunData liveRunData) {
        this.liveRunData = liveRunData;
    }

    void setLineFailures(LineFailures lineFailures) {
        this.lineFailures = lineFailures;
    }

    /**
     * The runtime failure of a 0-based line for fix with AI (Shift+F8): how many exchanges failed on the line in the
     * live run data, and the exception of the last one when known; null when nothing failed on it, or when the file is
     * edited and not saved (the live run data is of the saved file).
     */
    String runtimeFailure(int row) {
        String loadedFilePath = loadedFile.get();
        if (!failsAtRuntime(row)) {
            return null;
        }
        LiveLine live = liveLines().get(row);
        String answer = live.failed() + (live.failed() == 1 ? " exchange" : " exchanges") + " failed on this line";
        String last = lineFailures != null && loadedFilePath != null ? lineFailures.lastFailure(loadedFilePath, row) : null;
        return last != null ? answer + ", the last with " + last : answer;
    }

    /**
     * Whether processors on a 0-based line failed in the live run data, while the file is not edited (cheap: the hint
     * of each frame asks it; the exception is only read on Shift+F8).
     */
    boolean failsAtRuntime(int row) {
        if (dirty.getAsBoolean() || row < 0) {
            return false;
        }
        LiveLine live = liveLines().get(row);
        return live != null && live.failed() > 0;
    }

    /**
     * The live run data as a column of its own after the line numbers, so the numbers line up whatever the length of
     * the code: the exchanges right-aligned, then the failures and the mean time when any line has them, blank on the
     * lines without data. Nothing when the integration does not run.
     */
    void addLiveColumn(List<Span> spans, int lineIndex, Style selBg) {
        Map<Integer, LiveLine> lines = liveLines();
        if (lines.isEmpty() || liveTotalWidth == 0) {
            return;
        }
        LiveLine live = lines.get(lineIndex);
        boolean data = live != null && live.total() > 0;
        String total = data ? Long.toString(live.total()) : "";
        String failed = data && live.failed() > 0 ? "✗" + live.failed() : "";
        String mean = data && live.meanMillis() > 0 ? live.meanMillis() + "ms" : "";
        Style totalStyle = data ? Theme.label() : Style.EMPTY;
        Style failedStyle = Theme.error().bold();
        Style dim = Style.EMPTY.dim();
        if (selBg != null) {
            totalStyle = totalStyle.patch(selBg);
            failedStyle = failedStyle.patch(selBg);
            dim = dim.patch(selBg);
        }
        spans.add(Span.styled(" " + " ".repeat(liveTotalWidth - total.length()) + total, totalStyle));
        if (liveFailedWidth > 0) {
            spans.add(Span.styled(" " + failed + " ".repeat(liveFailedWidth - failed.length()), failedStyle));
        }
        if (liveMeanWidth > 0) {
            spans.add(Span.styled(" " + " ".repeat(liveMeanWidth - mean.length()) + mean, dim));
        }
        spans.add(Span.styled(" │", dim));
    }

    /** The live run data of the loaded file, read again at most once a second (the status is polled about as often). */
    private Map<Integer, LiveLine> liveLines() {
        String loadedFilePath = loadedFile.get();
        if (liveRunData == null || loadedFilePath == null) {
            return Collections.emptyMap();
        }
        long now = MonotonicClock.millis();
        if (now - liveLinesTime > 1000) {
            refreshLiveLines(now);
        }
        return liveLines;
    }

    /** Package-private for tests: reads the live run data again now, as the render does once a second. */
    void refreshLiveLinesForTesting() {
        refreshLiveLines(MonotonicClock.millis());
    }

    /** Package-private for tests: the text of the live column of a line. */
    String liveColumnForTesting(int lineIndex) {
        List<Span> spans = new ArrayList<>();
        addLiveColumn(spans, lineIndex, null);
        StringBuilder sb = new StringBuilder();
        spans.forEach(sp -> sb.append(sp.content()));
        return sb.toString();
    }

    private void refreshLiveLines(long now) {
        String loadedFilePath = loadedFile.get();
        {
            Map<Integer, LiveLine> lines = liveRunData.lines(loadedFilePath);
            liveLines = lines != null ? lines : Collections.emptyMap();
            liveLinesTime = now;
            // the widths of the column, for all the lines alike; they only grow while the same file is shown (from
            // a minimum), so the column does not jitter when a number crosses 99 -> 100 and back (a mean time going
            // up and down, a reset of the statistics); another file, or the integration stopping, starts afresh
            if (liveLines.isEmpty() || !loadedFilePath.equals(liveWidthsFile)) {
                liveTotalWidth = 0;
                liveFailedWidth = 0;
                liveMeanWidth = 0;
                liveWidthsFile = liveLines.isEmpty() ? null : loadedFilePath;
            }
            for (LiveLine l : liveLines.values()) {
                if (l.total() > 0) {
                    liveTotalWidth = Math.max(liveTotalWidth, Math.max(3, Long.toString(l.total()).length()));
                    if (l.failed() > 0) {
                        liveFailedWidth = Math.max(liveFailedWidth, Math.max(3, 1 + Long.toString(l.failed()).length()));
                    }
                    if (l.meanMillis() > 0) {
                        liveMeanWidth = Math.max(liveMeanWidth, Math.max(4, Long.toString(l.meanMillis()).length() + 2));
                    }
                }
            }
        }
    }
}
