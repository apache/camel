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
package org.apache.camel.dsl.jbang.launcher.selfupdate;

import java.util.regex.Pattern;

/**
 * The content of the web installer's {@code pinned-version} file, as written by {@code install.sh}/{@code install.ps1}
 * for an explicit {@code --version}/{@code -Version}: an exact release ({@code X.Y.Z}), a release line ({@code X.Y}),
 * or the newest LTS release ({@code LTS}). Anything else is {@link Kind#INVALID} and treated like an exact pin, so an
 * unexpected pin file never lets {@code camel self-update} move the install.
 */
public record Pin(Kind kind, String value) {

    private static final Pattern EXACT = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+");
    private static final Pattern LINE = Pattern.compile("[0-9]+\\.[0-9]+");

    public enum Kind {
        EXACT,
        LINE,
        LTS,
        INVALID
    }

    public static Pin parse(String raw) {
        String value = raw == null ? "" : raw.strip();
        if ("LTS".equalsIgnoreCase(value)) {
            return new Pin(Kind.LTS, "LTS");
        }
        if (EXACT.matcher(value).matches()) {
            return new Pin(Kind.EXACT, value);
        }
        if (LINE.matcher(value).matches()) {
            return new Pin(Kind.LINE, value);
        }
        return new Pin(Kind.INVALID, value);
    }

    /**
     * Whether this pin follows a published alias ({@code lts.properties} or {@code X.Y.properties}) rather than staying
     * on one version.
     */
    public boolean tracksAlias() {
        return kind == Kind.LINE || kind == Kind.LTS;
    }

    /**
     * The alias manifest name ({@code lts} or {@code X.Y}) for a {@link #tracksAlias() tracking} pin.
     */
    public String aliasName() {
        return switch (kind) {
            case LTS -> "lts";
            case LINE -> value;
            default -> throw new IllegalStateException("pin " + value + " does not track an alias");
        };
    }

    /**
     * Whether {@code version} (a valid X.Y.Z) satisfies this pin: any release for LTS, a release of the line for X.Y,
     * that exact release otherwise.
     */
    public boolean accepts(String version) {
        return switch (kind) {
            case LTS -> EXACT.matcher(version).matches();
            case LINE -> EXACT.matcher(version).matches() && version.startsWith(value + ".");
            case EXACT -> value.equals(version);
            default -> false;
        };
    }

    /**
     * Human-readable description, used in messages: {@code version 4.22.2}, {@code the 4.22 release line}, {@code LTS}.
     */
    public String describe() {
        return switch (kind) {
            case LTS -> "LTS";
            case LINE -> "the " + value + " release line";
            default -> "version " + value;
        };
    }
}
