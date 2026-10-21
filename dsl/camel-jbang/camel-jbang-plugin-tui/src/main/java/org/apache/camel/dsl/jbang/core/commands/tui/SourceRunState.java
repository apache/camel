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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

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
 * A failed reload puts back the routes that ran before it, so until a reload succeeds the state is the reload error. A
 * file whose routes are all stopped is shown as such; the facts of its lines still hold when the integration loaded it
 * as saved, which is known without a reload, but not for a route stopped after a reload.
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
        NOT_RUNNING,
        /** the routes the integration has from the file are all stopped (or suspended) */
        STOPPED
    }

    /**
     * @param kind   the state
     * @param routes the routes the integration has from the file
     * @param stale  how many of them were loaded before the file was saved, or at a time not known
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
        int started = 0;
        int stale = 0;
        int unknown = 0;
        for (RouteInfo r : info.routes) {
            if (LiveRunLines.lineOf(r.source, name) < 0) {
                continue;
            }
            routes++;
            boolean running = "Started".equals(r.state);
            if (running) {
                started++;
            }
            long uptime = running ? uptimeMillis(r.uptime) : -1;
            if (reloaded && uptime < 0) {
                // a stopped route after a reload: when it was loaded is not known, so it may be of another version
                unknown++;
            } else if ((reloaded ? Math.max(info.uptime, now - uptime) : info.uptime) < fileModified) {
                stale++;
            }
        }
        if (routes == 0) {
            return null;
        }
        if (info.reloadError != null) {
            return new State(Kind.RELOAD_FAILED, routes, stale + unknown, info.reloadError);
        }
        if (stale == 0) {
            // what a route stopped by hand says of the lines holds when it is the version that was loaded
            return new State(started == 0 ? Kind.STOPPED : Kind.RUNNING, routes, unknown, null);
        }
        stale += unknown;
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
            case STOPPED -> Line.from(Span.styled(TuiIcons.IDLE + " the routes of this file are stopped", Theme.muted()));
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
            case STOPPED -> "stopped";
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

        /** The state computed for a file: null when the integration has no route from it. */
        private record Computed(String file, State state) {
        }

        private volatile String file;
        private volatile Computed computed;
        // a save in the editor makes the state unknown until a refresh after it computes it again (written by the UI
        // thread only)
        private volatile int saves;
        private volatile int computedSaves;
        private boolean wasDirty;
        // the refreshes in the order they started: a later one wins over one that ends after it (guarded by this)
        private final AtomicLong refreshes = new AtomicLong();
        private long written;

        /**
         * The file the source panel shows, on the UI thread when it draws; null when none is. Edits that are no longer
         * unsaved were saved (or discarded): the state is unknown until the next refresh.
         *
         * @return whether the state is to be computed now: another file is shown, or the file was saved
         */
        boolean shown(String file, boolean dirty) {
            boolean saved = wasDirty && !dirty;
            if (saved) {
                saves++;
            }
            wasDirty = dirty;
            boolean other = !Objects.equals(file, this.file);
            this.file = file;
            return file != null && (saved || other);
        }

        /**
         * Computes the state of the file shown, off the UI thread: on the data refresh, when another file is shown or
         * the file is saved, and when tui_get_table reads the tab.
         */
        void refresh(IntegrationInfo info) {
            long order = refreshes.incrementAndGet();
            int at = saves;
            String shown = file;
            Computed result = null;
            if (shown != null && info != null) {
                try {
                    // the file system keeps the time of the save by the wall clock, so it is compared with the wall clock
                    long modified = Files.getLastModifiedTime(Path.of(shown)).toMillis();
                    result = new Computed(shown, of(info, shown, modified, System.currentTimeMillis()));
                } catch (IOException | RuntimeException e) {
                    // not known
                }
            }
            synchronized (this) {
                if (order > written) {
                    written = order;
                    computed = result;
                    computedSaves = at;
                }
            }
        }

        void reset() {
            file = null;
            synchronized (this) {
                // a refresh that started before does not write after
                written = refreshes.incrementAndGet();
                computed = null;
                computedSaves = saves;
            }
        }

        /**
         * The state of the file shown; null when unknown: computed for another file, or before the last save in the
         * editor.
         */
        State current() {
            Computed c = computed;
            String shown = file;
            return c != null && c.file().equals(shown) && computedSaves == saves ? c.state() : null;
        }

        /**
         * Whether the integration may run another version of the file than the one shown, so the live run data is of
         * other lines (CAMEL-25897): not known yet for the file shown (just opened, or saved in the editor since),
         * saved after the integration loaded it, a failed reload, or a route stopped after a reload.
         */
        boolean stale() {
            Computed c = computed;
            String shown = file;
            if (shown == null) {
                return false;
            }
            if (c == null || !c.file().equals(shown) || computedSaves != saves) {
                return true;
            }
            State state = c.state();
            return state != null && (state.kind() == Kind.RELOAD_FAILED || state.stale() > 0);
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
