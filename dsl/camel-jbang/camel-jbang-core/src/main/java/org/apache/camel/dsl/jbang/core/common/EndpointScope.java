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
package org.apache.camel.dsl.jbang.core.common;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;

/**
 * How far an endpoint reaches, as {@code camel get endpoint} and the Endpoint tab show it: {@link #REMOTE}, an external
 * system; {@link #INTERNAL}, Camel plumbing that stays inside the integration; or {@link #LOCAL}, neither. The three
 * exclude each other, as an internal component is never remote.
 */
public final class EndpointScope {

    /** An external system, over the network or a file system: the endpoint says so at runtime. */
    public static final String REMOTE = "remote";
    /** Stays inside the integration (direct, seda, log, mock): the catalog marks its component internal. */
    public static final String INTERNAL = "internal";
    /** Neither: the component can run code or a process, or read a file or a resource, on this host. */
    public static final String LOCAL = "local";

    private EndpointScope() {
    }

    /**
     * The scope of an endpoint.
     *
     * @param uri    the endpoint URI
     * @param remote whether the endpoint is remote, as the runtime reports it
     */
    public static String of(String uri, boolean remote) {
        if (remote) {
            return REMOTE;
        }
        return isInternal(scheme(uri)) ? INTERNAL : LOCAL;
    }

    /** The component of an endpoint URI: what comes before its first colon. */
    public static String scheme(String uri) {
        if (uri == null) {
            return null;
        }
        int colon = uri.indexOf(':');
        return colon > 0 ? uri.substring(0, colon) : uri;
    }

    /**
     * Whether a component stays inside the integration, as the catalog marks it internal: its routes, in-memory state
     * and log, and no file, network, process, or code or resource named in its URI, by any option. This is narrower
     * than remote=false, which exec, class, bean, language and the components that load a template or a stylesheet by
     * URI are too. The catalog is the CLI's own, so it has the flag; a component it does not know (a custom one, or one
     * newer than the CLI) is not internal.
     */
    public static boolean isInternal(String scheme) {
        if (scheme == null || !scheme.matches("[a-z0-9-]+")) {
            return false;
        }
        try {
            ComponentModel model = Catalog.INSTANCE.componentModel(scheme);
            return model != null && model.isInternal();
        } catch (RuntimeException e) {
            return false;
        }
    }

    // loaded when a scope is first asked for, not when the CLI starts
    private static final class Catalog {
        static final CamelCatalog INSTANCE = new DefaultCamelCatalog(true);
    }
}
