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
package org.apache.camel.dsl.jbang.core.commands.mcp;

/**
 * Shared description literals for {@code @ToolArg} annotations on MCP tools. Centralized to keep the MCP tool schema
 * payload (sent to clients on every session start) small.
 */
final class ToolArgDocs {

    /** Runtime selector with default. */
    static final String RUNTIME = "Runtime: main | spring-boot | quarkus (default: main)";

    /** Runtime selector without default. */
    static final String RUNTIME_REQUIRED = "Runtime: main | spring-boot | quarkus";

    /** Camel version for catalog lookups. */
    static final String CAMEL_VERSION = "Camel version (e.g., 4.17.0); defaults to catalog version.";

    /** Multi-runtime version selector; for quarkus use the Quarkus Platform version. */
    static final String VERSION_QUERY = "Version: main/spring-boot uses Camel version (e.g., 4.17.0); "
                                        + "quarkus uses Quarkus Platform version (e.g., 3.31.3) "
                                        + "from camel_version_list quarkusVersion. Defaults to catalog version.";

    /** Platform BOM override. */
    static final String PLATFORM_BOM = "Platform BOM GAV (groupId:artifactId:version); overrides camelVersion.";

    private ToolArgDocs() {
    }
}
