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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.catalog.cve.CveArtifact;
import org.apache.camel.catalog.cve.CveFinding;
import org.apache.camel.catalog.cve.CveScan;
import org.apache.camel.catalog.cve.CvssScore;

/**
 * The question the AI panel is opened with to explain a vulnerability (Shift+F8 on the CVE Audit tab, CAMEL-25362), or
 * the vulnerabilities of a JAR when the tab is grouped by artifact: the severity, the artifacts with the version that
 * fixes each and the chain that brings each in, whether the routes use them, the Camel advisory, and the change that
 * fixes each (CAMEL-27328): the version to declare, or the Camel release to upgrade to. A vulnerable library is not
 * always reachable from the routes, so the AI is asked to explain and offer the change, not to make it on its own. It
 * is put in the input of the panel, for the user to send with Enter or change first.
 */
final class CveAiPrompt {

    /** The description of a vulnerability can be long: its start says what it is. */
    static final int MAX_DETAILS = 1500;

    private static final String TAIL = "Tell whether the integration is affected: does it use the vulnerable part of"
                                       + " the library, from its routes and configuration? Then offer the change that"
                                       + " fixes it, from the suggested changes above. After a change, tui_get_table"
                                       + " on the Classpath tab tells whether only the fixed version is on the"
                                       + " classpath, and tui_get_table on the CVE Audit tab has the vulnerabilities."
                                       + " Make no changes to the files unless I agree.";

    private CveAiPrompt() {
    }

    static String of(IntegrationInfo info, CveFinding group) {
        StringBuilder sb = new StringBuilder();
        sb.append("Explain the vulnerability ").append(group.canonicalId()).append(" (").append(severity(group));
        if (group.cvssVector() != null) {
            sb.append(", ").append(group.cvssVector());
        }
        sb.append(") in the dependencies of the running integration").append(name(info));
        if (group.summary() != null && !group.summary().isBlank()) {
            sb.append(": ").append(group.summary().strip());
        }
        sb.append(".\n");
        List<String> affected = new ArrayList<>();
        for (String gav : group.affectedGavs()) {
            String fix = group.fixFor().get(gav);
            affected.add(gav + (fix != null ? ", fixed in " + fix : ", no fixed version known") + origin(group, gav));
        }
        if (!affected.isEmpty()) {
            sb.append("It affects: ").append(String.join("; ", affected)).append(".\n");
        }
        if (group.usedByRoutes() != null) {
            sb.append("Used by the routes: ").append(CveDetail.usageText(group.usedBy().values())).append(".\n");
        }
        appendCamelAdvisory(sb, group);
        if (group.fixedVersions().isEmpty() && group.fixFor().isEmpty()) {
            sb.append("No fixed version is known.\n");
        } else if (group.fixedVersions().size() > 1) {
            sb.append("Fixed versions of all lines: ").append(String.join(", ", group.fixedVersions())).append(".\n");
        }
        List<String> aliases = group.aliases().stream().filter(a -> !a.equals(group.canonicalId())).toList();
        if (!aliases.isEmpty()) {
            sb.append("Also known as: ").append(String.join(", ", aliases)).append(".\n");
        }
        if (group.published() != null) {
            sb.append("Published: ").append(group.published()).append(".\n");
        }
        if (group.details() != null && !group.details().isBlank()) {
            String details = group.details().strip();
            sb.append("Its description:\n").append(details.length() > MAX_DETAILS
                    ? details.substring(0, MAX_DETAILS) + "..." : details).append('\n');
        }
        Set<String> changes = new LinkedHashSet<>();
        for (String gav : group.affectedGavs()) {
            boolean camelRelease = group.camelAdvisory() != null && gav.startsWith(CveScan.CAMEL_GROUP_ID + ":");
            changes.add(change(gav, group.fixFor().get(gav), group.declared().contains(gav), group.gavVia().get(gav),
                    camelRelease));
        }
        appendChanges(sb, changes);
        sb.append(TAIL);
        return sb.toString();
    }

    static String ofArtifact(IntegrationInfo info, CveArtifact row) {
        StringBuilder sb = new StringBuilder();
        sb.append("Explain the vulnerabilities of ").append(row.gav()).append(", a dependency of the running integration")
                .append(name(info)).append(": ").append(row.cves().size()).append(row.cves().size() == 1 ? " CVE" : " CVEs")
                .append(", the worst ").append(row.severity());
        if (row.score() != null) {
            sb.append(" ").append(CvssScore.format(row.score()));
        }
        sb.append(".\n");
        for (CveFinding g : row.cves()) {
            String fix = g.fixFor().get(row.gav());
            sb.append("- ").append(g.canonicalId()).append(" (").append(severity(g)).append(")");
            if (g.summary() != null && !g.summary().isBlank()) {
                sb.append(": ").append(g.summary().strip());
            }
            sb.append(fix != null ? ", fixed in " + fix : ", no fixed version known").append(".\n");
        }
        if (row.via() != null) {
            sb.append("It comes in via ").append(row.via()).append(".\n");
        } else if (row.declared()) {
            sb.append("The project declares it itself.\n");
        }
        if (row.usage() != null) {
            sb.append("Used by the routes: ").append(CveDetail.usageText(List.of(row.usage()))).append(".\n");
        }
        if (row.fix() != null) {
            sb.append(row.camelRelease() ? "Camel " : "Version ").append(row.fix()).append(" fixes all of them.\n");
        } else {
            sb.append("No version is known to fix all of them.\n");
        }
        appendChanges(sb, Set.of(change(row.gav(), row.fix(), row.declared(), row.via(), row.camelRelease())));
        sb.append(TAIL);
        return sb.toString();
    }

    private static void appendCamelAdvisory(StringBuilder sb, CveFinding group) {
        if (group.camelAdvisory() == null) {
            return;
        }
        sb.append("Apache Camel advisory: ");
        if (group.camelAdvisory().upgradeTo() != null) {
            sb.append("upgrade to Camel ").append(group.camelAdvisory().upgradeTo()).append("; ");
        }
        sb.append("fixed in ").append(group.camelAdvisory().fixed()).append(".");
        if (group.camelAdvisory().mitigation() != null && !group.camelAdvisory().mitigation().isBlank()) {
            sb.append(" Mitigation: ").append(group.camelAdvisory().mitigation().strip());
        }
        sb.append("\n");
    }

    private static void appendChanges(StringBuilder sb, Set<String> changes) {
        sb.append("Suggested changes:\n");
        for (String change : changes) {
            sb.append("- ").append(change).append("\n");
        }
    }

    /** The change that fixes a JAR, from how it comes onto the classpath. */
    static String change(String gav, String fix, boolean declared, String via, boolean camelRelease) {
        String ga = CveScan.ga(gav);
        if (fix == null) {
            return ga + ": no fixed version is known; mitigate it (configuration, or not using the vulnerable"
                   + " feature) or replace the library";
        }
        if (camelRelease) {
            return ga + ": upgrade Camel to " + fix + " (the Camel version the integration runs with, such as"
                   + " camel.jbang.camel-version)";
        }
        if (declared) {
            return ga + ": use " + ga + ":" + fix + " where the project declares it (//DEPS,"
                   + " camel.jbang.dependencies or --dep)";
        }
        return ga + ": declare " + ga + ":" + fix + " in the project (camel.jbang.dependencies or //DEPS) to use the"
               + " fixed version instead of the one " + (via != null ? via : "the runtime") + " brings in, or upgrade"
               + " Camel when a newer release brings a fixed version";
    }

    private static String origin(CveFinding group, String gav) {
        String via = group.gavVia().get(gav);
        if (via != null) {
            return " (via " + via + ")";
        }
        return group.declared().contains(gav) ? " (declared by the project)" : "";
    }

    private static String severity(CveFinding group) {
        String sev = group.severity() != null ? group.severity() : "UNKNOWN";
        Double score = group.score();
        return score != null ? sev + " " + CvssScore.format(score) : sev;
    }

    private static String name(IntegrationInfo info) {
        String name = info != null ? info.name != null ? info.name : info.pid : null;
        return name != null ? " " + name : "";
    }
}
