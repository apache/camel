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

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.ResourceTemplateArg;
import io.quarkiverse.mcp.server.TextResourceContents;
import org.apache.camel.tooling.model.SecurityAdvisoryModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * MCP Resources exposing the published Apache Camel CVE security advisories from
 * <a href="https://camel.apache.org/security/">camel.apache.org/security</a> (shipped with the Camel catalog).
 * <p>
 * These resources provide browseable advisory data that clients can pull into context independently of route analysis.
 * When the catalog carries no advisory data, the resources return an explicit {@code advisoryDataUnavailable} marker
 * instead of an empty list, so a missing data set is never mistaken for "no known CVEs".
 */
@ApplicationScoped
public class AdvisoryResources {

    @Inject
    AdvisoryService advisoryService;

    /**
     * All published security advisories (one summary entry per CVE).
     */
    @Resource(uri = "camel://security/advisories",
              name = "camel_security_advisories_list",
              title = "Published Camel CVE Security Advisories",
              description = "List of all published Apache Camel CVE security advisories (the data behind "
                            + "https://camel.apache.org/security/) with severity, summary, fixed versions and link. "
                            + "The data ships with the Camel catalog bundled in this MCP server.",
              mimeType = "application/json")
    public TextResourceContents securityAdvisories() {
        JsonObject result = new JsonObject();
        try {
            JsonArray advisories = new JsonArray();
            for (SecurityAdvisoryModel advisory : advisoryService.advisories()) {
                JsonObject json = new JsonObject();
                json.put("cve", advisory.getCve());
                json.put("severity", advisory.getSeverity());
                json.put("summary", advisory.getSummary());
                json.put("fixed", advisory.getFixed());
                json.put("url", advisory.getUrl());
                advisories.add(json);
            }
            result.put("advisories", advisories);
            result.put("totalCount", advisories.size());
            result.put("source", AdvisoryService.SECURITY_PAGE_URL);
        } catch (Exception e) {
            result.put("advisoryDataUnavailable", true);
            result.put("message", e.getMessage());
        }
        return new TextResourceContents("camel://security/advisories", result.toJson(), "application/json");
    }

    /**
     * Full detail for a single published security advisory.
     */
    @ResourceTemplate(uriTemplate = "camel://security/advisory/{cve}",
                      name = "camel_security_advisory_detail",
                      title = "Camel CVE Security Advisory Detail",
                      description = "Full detail for a single published Apache Camel security advisory (by CVE id, "
                                    + "e.g. CVE-2025-27636) including affected and fixed versions, mitigation and "
                                    + "affected components.",
                      mimeType = "application/json")
    public TextResourceContents securityAdvisoryDetail(
            @ResourceTemplateArg(name = "cve") String cve) {

        String uri = "camel://security/advisory/" + cve;
        String wanted = cve == null ? "" : cve.trim();

        JsonObject result = new JsonObject();
        try {
            Optional<SecurityAdvisoryModel> found = advisoryService.advisories().stream()
                    .filter(advisory -> advisory.getCve().equalsIgnoreCase(wanted))
                    .findFirst();

            if (found.isEmpty()) {
                result.put("cve", cve);
                result.put("found", false);
                result.put("message", "No published Apache Camel security advisory found for '" + cve + "'. "
                                      + "See " + AdvisoryService.SECURITY_PAGE_URL + " for the full list.");
            } else {
                SecurityAdvisoryModel advisory = found.get();
                result.put("cve", advisory.getCve());
                result.put("found", true);
                result.put("severity", advisory.getSeverity());
                result.put("summary", advisory.getSummary());
                result.put("affected", advisory.getAffected());
                result.put("fixed", advisory.getFixed());
                result.put("mitigation", advisory.getMitigation());
                result.put("date", advisory.getDate());
                result.put("url", advisory.getUrl());
                JsonArray components = new JsonArray();
                components.addAll(advisory.getComponents());
                result.put("components", components);
            }
        } catch (Exception e) {
            result.put("advisoryDataUnavailable", true);
            result.put("message", e.getMessage());
        }
        return new TextResourceContents(uri, result.toJson(), "application/json");
    }
}
