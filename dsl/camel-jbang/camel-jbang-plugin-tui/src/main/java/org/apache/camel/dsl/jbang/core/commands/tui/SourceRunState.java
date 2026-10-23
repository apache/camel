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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

/**
 * Whether the selected integration runs the routes of the file open in the source editor as the file is saved, which
 * the Source tab shows on the editor (CAMEL-25771).
 * <p/>
 * The TUI does not load the file to tell. Loading a YAML or XML file with Camel runs parts of it, such as creating its
 * beans, and an endpoint written with parameters is built by its component, which the TUI does not have, so the
 * checksums would not be those of the running routes. It compares when the file was saved with when the integration
 * loaded the routes it has from the file:
 * <ul>
 * <li>an integration that has not reloaded loaded them when it started (a route stopped and started again keeps what it
 * loaded);</li>
 * <li>after a reload, a route started after the save runs what is saved. The integration does not say when it loaded a
 * route, so a route stopped and started again by hand after a reload is taken as loaded then.</li>
 * </ul>
 * A failed reload puts back the routes that ran before it, so until a reload succeeds the state is the reload error.
 */
final class SourceRunState {

    /** How long after a save an integration in dev mode is taken to be reloading the file. */
    static final long RELOAD_GRACE_MILLIS = 5000;

    enum Kind {
        /** every route the integration has from the file was loaded after the file was saved */
        RUNNING,
        /** saved after the routes were loaded, and the integration reloads on save: it is about to */
        RELOADING,
        /** the last reload failed, so the routes that ran before it run */
        RELOAD_FAILED,
        /** saved after the routes were loaded, and the integration did not load it again */
        NOT_RUNNING
    }

    /**
     * @param kind   the state
     * @param routes the routes the integration has from the file
     * @param stale  how many of them were loaded before the file was saved
     * @param detail why not running, or the reload error; null when running
     */
    record State(Kind kind, int routes, int stale, String detail) {
    }

    private SourceRunState() {
    }

    /**
     * @param  info         the selected integration
     * @param  filePath     the file open in the editor
     * @param  fileModified when the file was last saved, as the file system has it (the wall clock)
     * @param  now          the wall clock when the status of the integration was read
     * @return              the state, or null when the integration runs no route from the file
     */
    static State of(IntegrationInfo info, String filePath, long fileModified, long now) {
        String name = LiveRunLines.nameOf(filePath);
        if (info == null || name == null || fileModified <= 0) {
            return null;
        }
        // an integration that has not reloaded loaded its routes when it started; after a reload, when each started
        boolean reloaded = info.reloaded > 0 || info.reloadError != null || info.uptime <= 0;
        int routes = 0;
        int stale = 0;
        for (RouteInfo r : info.routes) {
            if (!"Started".equals(r.state) || LiveRunLines.lineOf(r.source, name) < 0) {
                continue;
            }
            long loaded = info.uptime;
            if (reloaded) {
                long uptime = uptimeMillis(r.uptime);
                if (uptime < 0) {
                    continue;
                }
                loaded = Math.max(info.uptime, now - uptime);
            }
            routes++;
            if (loaded < fileModified) {
                stale++;
            }
        }
        if (routes == 0) {
            return null;
        }
        if (info.reloadError != null) {
            return new State(Kind.RELOAD_FAILED, routes, stale, info.reloadError);
        }
        if (stale == 0) {
            return new State(Kind.RUNNING, routes, 0, null);
        }
        if (info.devMode) {
            if (now - fileModified < RELOAD_GRACE_MILLIS) {
                return new State(Kind.RELOADING, routes, stale, null);
            }
            return new State(Kind.NOT_RUNNING, routes, stale, "not reloaded since it was saved");
        }
        return new State(Kind.NOT_RUNNING, routes, stale, "the integration runs without --dev, restart it");
    }

    /** The uptime of a route as the route console writes it (5s, 1m5s, 2h5m, 1d3h); -1 when it has none. */
    static long uptimeMillis(String uptime) {
        if (uptime == null || uptime.isBlank()) {
            return -1;
        }
        try {
            return TimeUtils.toMilliSeconds(uptime.trim());
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    /** The state as the editor shows it: "● running as saved", "○ not running yet: ..." */
    static Line line(State state) {
        return switch (state.kind()) {
            case RUNNING -> Line.from(Span.styled(TuiIcons.SELECTED + " running as saved", Theme.success()));
            case RELOADING -> Line.from(Span.styled(TuiIcons.IDLE + " reloading...", Theme.muted()));
            case RELOAD_FAILED -> Line.from(
                    Span.styled(TuiIcons.WARN + " the reload failed", Theme.error()),
                    Span.styled(": " + TuiHelper.fixControlChars(state.detail().lines().findFirst().orElse("")),
                            Theme.muted()));
            case NOT_RUNNING -> Line.from(
                    Span.styled(TuiIcons.IDLE + " not running yet", Theme.warning()),
                    Span.styled(": " + state.detail(), Theme.muted()));
        };
    }

    /** The state for tui_get_table, so an AI agent can tell whether its edit runs. */
    static JsonObject toJson(State state) {
        JsonObject jo = new JsonObject();
        jo.put("state", switch (state.kind()) {
            case RUNNING -> "running";
            case RELOADING -> "reloading";
            case RELOAD_FAILED -> "reloadFailed";
            case NOT_RUNNING -> "notRunning";
        });
        jo.put("routes", state.routes());
        jo.put("routesLoadedBeforeSave", state.stale());
        if (state.detail() != null) {
            jo.put("detail", state.detail());
        }
        return jo;
    }

    /**
     * What the Source tab keeps of it: the file it last drew, and the state computed for that file on the background
     * thread of the data refresh, as it reads when the file was saved.
     */
    static final class Tracker {

        private record Computed(String file, State state) {
        }

        private volatile String file;
        private volatile Computed computed;

        /** The file the source panel shows, on the UI thread when it draws; null when none is. */
        void shown(String file) {
            this.file = file;
        }

        /** Computes the state of the file shown, on the background thread of the data refresh. */
        void refresh(IntegrationInfo info) {
            String shown = file;
            if (shown == null || info == null) {
                computed = null;
                return;
            }
            try {
                // the file system keeps the time of the save by the wall clock, so it is compared with the wall clock
                long modified = Files.getLastModifiedTime(Path.of(shown)).toMillis();
                State state = of(info, shown, modified, System.currentTimeMillis());
                computed = state != null ? new Computed(shown, state) : null;
            } catch (IOException | RuntimeException e) {
                computed = null;
            }
        }

        void reset() {
            file = null;
            computed = null;
        }

        /** The state of the file shown; null when unknown, or when it was computed for another file. */
        State current() {
            Computed c = computed;
            String shown = file;
            return c != null && c.file().equals(shown) ? c.state() : null;
        }

        /** The state on the bottom border of the source panel, left of the cursor position of the editor. */
        void render(Frame frame, Rect area) {
            State state = current();
            // the cursor position takes the right of the border (" row:999 col:999 ")
            int width = area.width() - 4 - 18;
            if (state == null || area.height() < 3 || width < 12) {
                return;
            }
            List<Span> spans = new ArrayList<>();
            spans.add(Span.raw(" "));
            spans.addAll(line(state).spans());
            spans.add(Span.raw(" "));
            Line line = Line.from(spans);
            frame.renderWidget(Paragraph.builder().text(Text.from(line)).build(),
                    new Rect(area.x() + 2, area.y() + area.height() - 1, Math.min(width, line.width()), 1));
        }

        /** The file shown and its state, for tui_get_table. */
        void addTo(JsonObject json) {
            String shown = file;
            if (shown != null) {
                json.put("openFile", shown);
                State state = current();
                if (state != null) {
                    json.put("runState", toJson(state));
                }
            }
        }
    }
}
