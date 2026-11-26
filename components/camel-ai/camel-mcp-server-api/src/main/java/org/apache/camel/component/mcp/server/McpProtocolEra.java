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
package org.apache.camel.component.mcp.server;

import java.util.Locale;

/**
 * The MCP protocol versions an engine serves, named after the eras of the MCP specification (revision 2026-07-28,
 * "Versioning and Compatibility"): legacy versions open with an {@code initialize} handshake (2025-11-25 and earlier),
 * modern versions carry their protocol version on every request and have no session (2026-07-28 and later).
 *
 * @since 4.24
 */
public enum McpProtocolEra {

    /**
     * Only the protocol versions that open with an {@code initialize} handshake.
     */
    LEGACY,

    /**
     * Both eras on the same endpoint, chosen for each request from the protocol version it carries (the default).
     */
    DUAL,

    /**
     * Only protocol version 2026-07-28 and later; an {@code initialize} request is answered with an error that lists
     * the supported versions.
     */
    MODERN;

    /**
     * Whether clients of the protocol versions that open with an {@code initialize} handshake are served.
     */
    public boolean servesLegacy() {
        return this != MODERN;
    }

    /**
     * Whether clients of protocol version 2026-07-28 are served.
     */
    public boolean servesModern() {
        return this != LEGACY;
    }

    /**
     * The configuration value of this era, as in {@code camel.server.mcp-protocol-era}.
     */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a configuration value, ignoring case and surrounding spaces.
     *
     * @param  value                    {@code legacy}, {@code dual} or {@code modern}; {@code null} or blank for
     *                                  {@link #DUAL}
     * @return                          the era
     * @throws IllegalArgumentException when the value is not one of the eras
     */
    public static McpProtocolEra parse(String value) {
        if (value == null || value.isBlank()) {
            return DUAL;
        }
        String trimmed = value.trim();
        for (McpProtocolEra era : values()) {
            if (era.name().equalsIgnoreCase(trimmed)) {
                return era;
            }
        }
        throw new IllegalArgumentException(
                "Invalid MCP protocol era '" + trimmed + "': expected legacy, dual or modern");
    }
}
