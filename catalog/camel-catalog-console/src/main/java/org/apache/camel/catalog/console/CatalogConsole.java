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
package org.apache.camel.catalog.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.tooling.model.OtherModel;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "catalog", description = "Information about used Camel artifacts")
@SuppressWarnings("java:S2160")
public class CatalogConsole extends AbstractDevConsole {

    public record ArtifactEntry(
            @Metadata(description = "The Maven group ID") String groupId,
            @Metadata(description = "The Maven artifact ID") String artifactId,
            @Metadata(description = "The Maven version") String version,
            @Metadata(description = "The support level, optionally suffixed with -deprecated") String level,
            @Metadata(description = "The first Camel version the artifact was introduced in") String firstVersion,
            @Metadata(description = "The artifact title") String title,
            @Metadata(description = "The artifact description") String description) {
    }

    public record Response(
            @Metadata(description = "The components in use") List<ArtifactEntry> components,
            @Metadata(description = "The data formats in use") List<ArtifactEntry> dataformat,
            @Metadata(description = "The languages in use") List<ArtifactEntry> languages,
            @Metadata(description = "Other miscellaneous artifacts in use, discovered via the classpath") List<ArtifactEntry> others) {
    }

    private static final String CP = System.getProperty("java.class.path");
    private final CamelCatalog catalog = new DefaultCamelCatalog(true);

    public CatalogConsole() {
        super("camel", "catalog", "Catalog", "Information about used Camel artifacts");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        sb.append("\nComponents:\n");
        getCamelContext().getComponentNames().forEach(n -> appendModel(catalog.componentModel(n), sb));
        sb.append("\n\nLanguages:\n");
        getCamelContext().getLanguageNames().forEach(n -> appendModel(catalog.languageModel(n), sb));
        sb.append("\n\nData Formats:\n");
        getCamelContext().getDataFormatNames().forEach(n -> appendModel(catalog.dataFormatModel(n), sb));

        // misc is harder to find as we need to find them via classpath
        sb.append("\n\nMiscellaneous Components:\n");
        evalMisc(sb, CatalogConsole::appendModel);

        return sb.toString();
    }

    private <T> void evalMisc(T consumable, BiConsumer<ArtifactModel<?>, T> consumer) {
        String[] cp = CP.split("[:|;]");
        String suffix = "-" + getCamelContext().getVersion() + ".jar";
        for (String c : cp) {
            if (c.endsWith(suffix)) {
                int pos = Math.max(c.lastIndexOf("/"), c.lastIndexOf("\\"));
                if (pos > 0) {
                    c = c.substring(pos + 1, c.length() - suffix.length());
                    consumer.accept(findOtherModel(c), consumable);
                }
            }
        }
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        List<ArtifactEntry> components = new ArrayList<>();
        List<ArtifactEntry> dataformat = new ArrayList<>();
        List<ArtifactEntry> languages = new ArrayList<>();
        List<ArtifactEntry> others = new ArrayList<>();

        getCamelContext().getComponentNames().forEach(n -> appendModel(catalog.componentModel(n), components));
        getCamelContext().getLanguageNames().forEach(n -> appendModel(catalog.languageModel(n), languages));
        getCamelContext().getDataFormatNames().forEach(n -> appendModel(catalog.dataFormatModel(n), dataformat));

        // misc is harder to find as we need to find them via classpath
        evalMisc(others, CatalogConsole::appendModel);

        Response response = new Response(components, dataformat, languages, others);
        return JsonRecordSupport.toJsonObject(response);
    }

    private ArtifactModel<?> findOtherModel(String artifactId) {
        // is it a mist component
        for (String name : catalog.findOtherNames()) {
            OtherModel model = catalog.otherModel(name);
            if (model != null && model.getArtifactId().equals(artifactId)) {
                return model;
            }
        }
        return null;
    }

    private static void appendModel(ArtifactModel<?> model, StringBuilder sb) {
        if (model != null) {
            String level = model.getSupportLevel().toString();
            if (model.isDeprecated()) {
                level += "-deprecated";
            }
            sb.append(String.format("%n    %s %s %s %s: %s", model.getArtifactId(), level,
                    model.getFirstVersionShort(), model.getTitle(), model.getDescription()));
        }
    }

    private static void appendModel(ArtifactModel<?> model, List<ArtifactEntry> list) {
        if (model != null) {
            String level = model.getSupportLevel().toString();
            if (model.isDeprecated()) {
                level += "-deprecated";
            }
            list.add(new ArtifactEntry(
                    model.getGroupId(), model.getArtifactId(), model.getVersion(), level,
                    model.getFirstVersionShort(), model.getTitle(), model.getDescription()));
        }
    }
}
