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

import org.apache.camel.dsl.jbang.core.commands.tui.CveAuditTab.ArtifactRow;
import org.apache.camel.dsl.jbang.core.commands.tui.CveAuditTab.CamelAdvisory;
import org.apache.camel.dsl.jbang.core.commands.tui.CveAuditTab.VulnGroup;

/**
 * The detail panel of the CVE Audit tab, as Markdown: of a CVE, or of a JAR with its CVEs.
 */
final class CveDetail {

    private CveDetail() {
    }

    /** A CVE: its severity and score, the JARs it affects with the version that fixes each, the Camel advisory. */
    static String cve(VulnGroup group) {
        StringBuilder md = new StringBuilder();
        md.append("**Severity:** ").append(group.severity != null ? group.severity : "UNKNOWN");
        Double score = group.score();
        if (score != null) {
            md.append(" ").append(CvssScore.format(score));
        }
        if (group.cvssVector != null) {
            md.append(" (`").append(group.cvssVector).append("`)");
        }
        md.append("  \n");
        if (group.published != null) {
            String date = group.published.length() > 10 ? group.published.substring(0, 10) : group.published;
            md.append("**Published:** ").append(date).append("  \n");
        }
        if (group.usedByRoutes != null) {
            md.append("**Used by the routes:** ").append(usageText(group.usedBy.values())).append("  \n");
        }
        md.append("\n");
        if (group.summary != null) {
            md.append(group.summary).append("\n\n");
        }
        md.append("**Affected artifacts:**\n");
        for (String gav : group.affectedGavs) {
            md.append("- `").append(gav).append("`");
            String fix = group.fixFor.get(gav);
            md.append(fix != null ? ", fixed in **" + fix + "**" : ", no fixed version known");
            md.append(viaText(group, gav));
            md.append("\n");
        }
        CamelAdvisory advisory = group.camelAdvisory;
        if (advisory != null) {
            md.append("\n**Apache Camel advisory:** ");
            if (advisory.upgradeTo() != null) {
                md.append("upgrade to Camel **").append(advisory.upgradeTo()).append("**. ");
            }
            if (advisory.fixed() != null) {
                md.append("Fixed in ").append(advisory.fixed()).append(".");
            }
            md.append("\n");
            if (advisory.mitigation() != null && !advisory.mitigation().isBlank()) {
                md.append("\n").append(advisory.mitigation().strip()).append("\n");
            }
            if (advisory.url() != null) {
                md.append("\n").append(advisory.url()).append("\n");
            }
        }
        if (group.fixedVersions.size() > 1) {
            md.append("\n**Fixed versions (all lines):** ").append(String.join(", ", group.fixedVersions))
                    .append("\n");
        }
        if (!group.aliases.isEmpty()) {
            md.append("\n**Aliases:** ").append(String.join(", ", group.aliases)).append("\n");
        }
        md.append("\nhttps://osv.dev/vulnerability/").append(group.canonicalId).append("\n");
        if (group.details != null && !group.details.isEmpty()) {
            md.append("\n---\n\n").append(group.details).append("\n");
        }
        return md.toString();
    }

    /** A JAR: the version that fixes all its CVEs, how it comes in, whether the routes use it, and its CVEs. */
    static String artifact(ArtifactRow row) {
        StringBuilder md = new StringBuilder();
        md.append("**Severity:** ").append(row.severity);
        if (row.score != null) {
            md.append(" ").append(CvssScore.format(row.score));
        }
        md.append(" (the worst of ").append(row.cves.size()).append(row.cves.size() == 1 ? " CVE)" : " CVEs)")
                .append("  \n");
        if (row.via != null) {
            md.append("**Via:** ").append(row.via).append("  \n");
        } else if (row.declared) {
            md.append("**Via:** declared by the project  \n");
        }
        if (row.usage != null) {
            md.append("**Used by the routes:** ").append(usageText(List.of(row.usage))).append("  \n");
        }
        md.append("\n");
        if (row.fix != null) {
            md.append("**Upgrade to ").append(row.camelRelease ? "Camel " : "").append(row.fix)
                    .append("**, which fixes ").append(row.cves.size() == 1 ? "it" : "all " + row.cves.size())
                    .append(".\n\n");
        } else {
            md.append("**No version is known to fix all of them.**\n\n");
        }
        md.append("**CVEs:**\n");
        for (VulnGroup g : row.cves) {
            md.append("- **").append(g.severity);
            Double score = g.score();
            if (score != null) {
                md.append(" ").append(CvssScore.format(score));
            }
            md.append("** ").append(g.canonicalId);
            if (g.summary != null) {
                md.append(": ").append(g.summary.strip());
            }
            String fix = g.fixFor.get(row.gav);
            md.append(fix != null ? " (fixed in " + fix + ")" : " (no fix known)");
            md.append("\n");
        }
        return md.toString();
    }

    private static String viaText(VulnGroup group, String gav) {
        String via = group.gavVia.get(gav);
        if (via != null) {
            return " *(via " + via + ")*";
        }
        return group.declared.contains(gav) ? " *(declared by the project)*" : "";
    }

    /** Whether the routes use the components: yes when one is used, else no. */
    static String usageText(Iterable<CveScan.Usage> usages) {
        List<String> used = new ArrayList<>();
        List<String> unused = new ArrayList<>();
        for (CveScan.Usage u : usages) {
            List<String> list = u.used() ? used : unused;
            if (!list.contains(u.component())) {
                list.add(u.component());
            }
        }
        if (!used.isEmpty()) {
            return "yes, the routes have endpoints of " + String.join(", ", used);
        }
        return "no, the routes have no endpoint of " + String.join(", ", unused);
    }
}
