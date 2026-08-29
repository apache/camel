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
package org.apache.camel.impl.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.CatalogCamelContext;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.tooling.model.DevConsoleModel;
import org.apache.camel.tooling.model.DevConsoleOpenApiHelper;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

@DevConsole(name = "api", displayName = "API", description = "OpenAPI specification for the dev console API")
@Configurer(extended = true)
public class ApiDevConsole extends AbstractDevConsole {

    private volatile String cachedOpenApi;

    public ApiDevConsole() {
        super("camel", "api", "API", "OpenAPI specification for the dev console API");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        if (cachedOpenApi == null) {
            cachedOpenApi = buildOpenApi();
        }
        return cachedOpenApi;
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        if (cachedOpenApi == null) {
            cachedOpenApi = buildOpenApi();
        }
        // parse the cached string back into a JsonObject so it integrates
        // with the dev console JSON response structure
        try {
            return (JsonObject) Jsoner.deserialize(cachedOpenApi);
        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.put("error", e.getMessage());
            return error;
        }
    }

    private String buildOpenApi() {
        List<DevConsoleModel> models = new ArrayList<>();

        DevConsoleRegistry dcr = getCamelContext().getCamelContextExtension()
                .getContextPlugin(DevConsoleRegistry.class);
        if (dcr != null && dcr.isEnabled()) {
            for (org.apache.camel.console.DevConsole console : dcr.stream().toList()) {
                models.add(buildConsoleModel(console));
            }
        }

        JsonObject root = DevConsoleOpenApiHelper.buildOpenApiDocument(models, getCamelContext().getVersion());
        return Jsoner.prettyPrint(root.toJson());
    }

    /**
     * Builds the {@link DevConsoleModel} for the given live console: its options are loaded from the console's
     * generated catalog schema, while id/displayName/description/readOnly come from the live instance.
     */
    private DevConsoleModel buildConsoleModel(org.apache.camel.console.DevConsole console) {
        DevConsoleModel model;
        try {
            String json = ((CatalogCamelContext) getCamelContext())
                    .getDevConsoleParameterJsonSchema(console.getId());
            model = json != null ? JsonMapper.generateDevConsoleModel(json) : new DevConsoleModel();
        } catch (Exception e) {
            model = new DevConsoleModel();
        }
        model.setName(console.getId());
        model.setTitle(console.getDisplayName());
        model.setDescription(console.getDescription());
        model.setReadOnly(console.isReadOnly());
        return model;
    }
}
