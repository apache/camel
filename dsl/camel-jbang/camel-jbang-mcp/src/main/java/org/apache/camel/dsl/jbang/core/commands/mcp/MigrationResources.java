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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.ResourceTemplateArg;
import io.quarkiverse.mcp.server.TextResourceContents;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * MCP Resources exposing migration guide reference data.
 * <p>
 * These resources provide browseable migration guide metadata that clients can pull into context for Camel version
 * upgrades and runtime migrations.
 */
@ApplicationScoped
public class MigrationResources {

    @Inject
    MigrationData migrationData;

    /**
     * All Camel migration guides with titles, URLs, and summaries.
     */
    @Resource(uri = "camel://migration/guides",
              name = "camel_migration_guides",
              title = "Camel Migration Guides",
              description = "List of all Camel migration and upgrade guides with titles, URLs, and summaries "
                            + "covering Camel 2.x to 3.x, 3.x to 4.x, and minor version upgrades.",
              mimeType = "application/json")
    public TextResourceContents migrationGuides() {
        JsonObject result = new JsonObject();

        JsonArray guides = new JsonArray();
        for (MigrationData.MigrationGuide guide : migrationData.getMigrationGuides()) {
            JsonObject guideJson = new JsonObject();
            guideJson.put("name", guide.name());
            guideJson.put("title", guide.title());
            guideJson.put("url", guide.url());
            guideJson.put("summary", guide.summary());
            guides.add(guideJson);
        }

        result.put("guides", guides);
        result.put("totalCount", guides.size());

        return new TextResourceContents("camel://migration/guides", result.toJson(), "application/json");
    }

    /**
     * Detail for a specific migration guide by name.
     */
    @ResourceTemplate(uriTemplate = "camel://migration/guide/{name}",
                      name = "camel_migration_guide_detail",
                      title = "Migration Guide Detail",
                      description = "Detail for a specific Camel migration guide including title, URL, and summary.",
                      mimeType = "application/json")
    public TextResourceContents migrationGuideDetail(
            @ResourceTemplateArg(name = "name") String name) {

        String uri = "camel://migration/guide/" + name;

        MigrationData.MigrationGuide guide = migrationData.getMigrationGuide(name);
        if (guide == null) {
            JsonObject result = new JsonObject();
            result.put("name", name);
            result.put("found", false);
            result.put("message", "Migration guide '" + name + "' not found. "
                                  + "Available guides: migration-and-upgrade, camel-3-migration, "
                                  + "camel-4-migration, camel-3x-upgrade, camel-4x-upgrade.");
            return new TextResourceContents(uri, result.toJson(), "application/json");
        }

        JsonObject result = new JsonObject();
        result.put("name", guide.name());
        result.put("found", true);
        result.put("title", guide.title());
        result.put("url", guide.url());
        result.put("summary", guide.summary());

        return new TextResourceContents(uri, result.toJson(), "application/json");
    }
}
