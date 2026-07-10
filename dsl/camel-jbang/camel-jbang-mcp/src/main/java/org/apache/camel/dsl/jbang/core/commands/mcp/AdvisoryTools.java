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

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.tooling.model.SecurityAdvisoryModel;

/**
 * MCP Tool exposing the published Apache Camel CVE security advisories from
 * <a href="https://camel.apache.org/security/">camel.apache.org/security</a> (shipped with the Camel catalog).
 * <p>
 * Lets an LLM answer questions such as "is my Camel 4.10.1 project affected by known CVEs?" or "which CVEs were
 * published for camel-kafka and in which versions are they fixed?".
 */
@ApplicationScoped
public class AdvisoryTools {

    @Inject
    AdvisoryService advisoryService;

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "List published Apache Camel CVE security advisories (the data behind "
                        + "https://camel.apache.org/security/), optionally filtered by Camel version, component and "
                        + "severity. When camelVersion is set, advisories whose parsed affected ranges exclude that "
                        + "version are dropped; advisories whose ranges cannot be parsed are kept with "
                        + "affectsGivenVersion unset - judge those from the 'affected' text. The advisory data ships "
                        + "with the Camel catalog (synced from the published advisories when Camel is released), so "
                        + "advisories published after this Camel version was released are not included - check the "
                        + "web page for the very latest.")
    public AdvisoriesResult camel_security_advisories(
            @ToolArg(description = "Camel version to check, e.g. 4.10.1 (optional)") String camelVersion,
            @ToolArg(description = "Component to filter by, e.g. kafka or camel-kafka (optional; best-effort match "
                                   + "against components named in the advisory text - older advisories may not name "
                                   + "components)") String component,
            @ToolArg(description = "Severity to filter by as published, e.g. LOW, MEDIUM, MODERATE, IMPORTANT or "
                                   + "CRITICAL (optional)") String severity) {
        try {
            List<SecurityAdvisoryModel> advisories = advisoryService.advisories();
            List<AdvisoryService.AdvisoryView> matches
                    = AdvisoryService.query(advisories, camelVersion, component, severity);
            return new AdvisoriesResult(
                    AdvisoryService.SECURITY_PAGE_URL,
                    advisories.size(),
                    matches.size(),
                    blankToNull(camelVersion),
                    blankToNull(component),
                    blankToNull(severity),
                    matches);
        } catch (AdvisoryService.AdvisoriesUnavailableException e) {
            throw new ToolCallException(e.getMessage(), e);
        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to load security advisories (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // Result records

    public record AdvisoriesResult(
            String source, int totalPublished, int matched, String camelVersion, String component, String severity,
            List<AdvisoryService.AdvisoryView> advisories) {
    }
}
