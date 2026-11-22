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
import java.util.Map;

import org.apache.camel.dsl.jbang.core.common.outdated.CamelRelease;
import org.apache.camel.dsl.jbang.core.common.outdated.DependencyRow;

/**
 * The question the AI panel is opened with on the Maven Dependencies tab (Shift+F8, CAMEL-27351): where the Camel
 * version stands, and the dependencies worth a look, each with what the check found (a version other than the one Camel
 * brings, loaded twice, newer releases, CVEs). An upgrade past the version Camel brings, or to another major version,
 * may not work, so the AI is asked which upgrades are safe and to offer the changes, not to make them.
 */
final class DependencyAiPrompt {

    /** A project can have hundreds of dependencies: the prompt lists the ones worth a look, up to this many. */
    static final int MAX_ROWS = 40;

    private static final String TAIL = "Say which dependencies to upgrade, to which version, and whether that is safe:"
                                       + " a version newer than the one the Camel dependencies bring, or another"
                                       + " major version, may not work with this Camel version. Keep the Camel JARs"
                                       + " on one Camel version. For a dependency the project declares, the change is"
                                       + " where it declares it (//DEPS, camel.jbang.dependencies, --dep or the"
                                       + " pom.xml); for one that comes in with another, upgrade or exclude the one"
                                       + " that brings it. After a change, tui_get_table on the Maven Dependencies"
                                       + " tab and the Classpath tab tell whether only the version you want is"
                                       + " loaded. Make no changes to the files unless I agree.";

    private DependencyAiPrompt() {
    }

    static String of(
            IntegrationInfo info, CamelRelease release, String camelVersion, List<DependencyRow> rows,
            Map<String, CveAuditTab.CveCount> cves, DependencyRow selected, boolean newerChecked) {
        StringBuilder sb = new StringBuilder();
        sb.append("Review the dependencies of the running integration").append(name(info)).append(".\n");
        if (release != null) {
            sb.append(release.summary()).append(".\n");
        } else if (camelVersion != null) {
            sb.append("Camel ").append(camelVersion).append(".\n");
        }
        List<DependencyRow> worth = new ArrayList<>();
        if (selected != null) {
            worth.add(selected);
        }
        for (DependencyRow r : rows) {
            if (r != selected && (r.outdated(true) || (cves != null && cves.containsKey(r.gav())))) {
                worth.add(r);
            }
        }
        if (worth.isEmpty()) {
            sb.append("None of its ").append(rows.size()).append(" dependencies overrides the version Camel brings")
                    .append(" or is loaded twice");
            sb.append(newerChecked ? ", and none has a newer release.\n" : ".\n");
        } else {
            sb.append(selected != null ? "Start with the first one. " : "")
                    .append("The dependencies worth a look, of ").append(rows.size()).append(":\n");
            int n = 0;
            for (DependencyRow r : worth) {
                if (n++ == MAX_ROWS) {
                    sb.append("- and ").append(worth.size() - MAX_ROWS).append(" more\n");
                    break;
                }
                sb.append("- ").append(line(r, cves != null ? cves.get(r.gav()) : null)).append('\n');
            }
        }
        if (!newerChecked) {
            sb.append("The newer releases were not looked up (u on the tab looks them up, with the CVEs).\n");
        }
        sb.append(TAIL);
        return sb.toString();
    }

    static String line(DependencyRow r, CveAuditTab.CveCount cve) {
        StringBuilder sb = new StringBuilder(r.gav());
        if (r.transitive()) {
            sb.append(r.via() != null ? " (comes in with " + r.via() + ")" : " (transitive)");
        } else {
            sb.append(" (declared)");
        }
        sb.append(": ").append(r.explain());
        if (r.newer() != null) {
            sb.append("; newer on this line: ").append(r.newer());
        }
        if (r.latest() != null && !r.latest().equals(r.newer())) {
            sb.append("; latest: ").append(r.latest()).append(r.latestIsMajor() ? " (another major version)" : "");
        }
        if (cve != null) {
            sb.append("; ").append(MavenDependenciesTab.cveText(cve));
        }
        return sb.toString();
    }

    private static String name(IntegrationInfo info) {
        String name = info != null ? info.name != null ? info.name : info.pid : null;
        return name != null ? " " + name : "";
    }
}
