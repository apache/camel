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
 * The question the AI panel is opened with to explain a vulnerability (Shift+F8 on the CVE Audit tab, CAMEL-25362): its
 * severity, the artifacts it affects and the dependency that brings each in, the versions that fix it, and its
 * description. A vulnerable library is not always reachable from the routes, and the fix may be a Camel upgrade, so the
 * AI is asked to explain and suggest, not to change the files on its own. It is put in the input of the panel, for the
 * user to send with Enter or change first.
 */
final class CveAiPrompt {

    /** The description of a vulnerability can be long: its start says what it is. */
    static final int MAX_DETAILS = 1500;

    private CveAiPrompt() {
    }

    static String of(IntegrationInfo info, CveAuditTab.VulnGroup group) {
        StringBuilder sb = new StringBuilder();
        String name = info != null ? info.name != null ? info.name : info.pid : null;
        sb.append("Explain the vulnerability ").append(group.canonicalId).append(" (")
                .append(group.severity != null ? group.severity : "UNKNOWN");
        if (group.cvssVector != null) {
            sb.append(", ").append(group.cvssVector);
        }
        sb.append(") in the dependencies of the running integration").append(name != null ? " " + name : "");
        if (group.summary != null && !group.summary.isBlank()) {
            sb.append(": ").append(group.summary.strip());
        }
        sb.append(".\n");
        List<String> affected = new ArrayList<>();
        for (String gav : group.affectedGavs) {
            String parent = group.gavParents.get(gav);
            affected.add(parent != null ? gav + " (via " + parent + ")" : gav);
        }
        if (!affected.isEmpty()) {
            sb.append("It affects: ").append(String.join(", ", affected)).append(".\n");
        }
        if (!group.fixedVersions.isEmpty()) {
            sb.append("Fixed in: ").append(String.join(", ", group.fixedVersions)).append(".\n");
        } else {
            sb.append("No fixed version is known.\n");
        }
        List<String> aliases = group.aliases.stream().filter(a -> !a.equals(group.canonicalId)).toList();
        if (!aliases.isEmpty()) {
            sb.append("Also known as: ").append(String.join(", ", aliases)).append(".\n");
        }
        if (group.published != null) {
            sb.append("Published: ").append(group.published).append(".\n");
        }
        if (group.details != null && !group.details.isBlank()) {
            String details = group.details.strip();
            sb.append("Its description:\n").append(details.length() > MAX_DETAILS
                    ? details.substring(0, MAX_DETAILS) + "..." : details).append('\n');
        }
        sb.append("Tell whether the integration is affected: does it use the vulnerable part of the library, from its")
                .append(" routes and configuration? Then say how to fix it: when the project declares the artifact")
                .append(" itself (//DEPS, camel.jbang.dependencies or --dep), a fixed version to use; when it comes")
                .append(" with a Camel component (via camel-...), whether a newer Camel version has the fix, or how to")
                .append(" use a fixed version meanwhile. tui_get_table on the CVE Audit tab has the vulnerabilities.")
                .append(" Make no changes to the files unless I agree.");
        return sb.toString();
    }
}
