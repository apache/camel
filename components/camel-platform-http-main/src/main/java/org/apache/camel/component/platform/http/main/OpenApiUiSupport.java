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
package org.apache.camel.component.platform.http.main;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.StaticHandler;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpRouter;
import org.apache.camel.util.ObjectHelper;

/**
 * Serves Swagger UI at {@value #OPENAPI_UI_PATH} for exploring REST DSL OpenAPI documents.
 */
final class OpenApiUiSupport {

    static final String OPENAPI_UI_PATH = "/q/openapi";
    static final String DEFAULT_SPEC_PATH = "/q/openapi.json";

    /**
     * Version of the bundled org.webjars:swagger-ui artifact (must match pom.xml).
     */
    private static final String SWAGGER_UI_WEBJAR_VERSION = "5.21.0";

    private OpenApiUiSupport() {
    }

    static void setup(VertxPlatformHttpRouter router, PlatformHttpComponent platformHttpComponent, String specPath) {
        ObjectHelper.notNull(router, "router");
        ObjectHelper.notNull(platformHttpComponent, "platformHttpComponent");
        String spec = ObjectHelper.isNotEmpty(specPath) ? specPath : DEFAULT_SPEC_PATH;
        if (!spec.startsWith("/")) {
            spec = "/" + spec;
        }

        String webjarsRoot = "META-INF/resources/webjars/swagger-ui/" + SWAGGER_UI_WEBJAR_VERSION;
        StaticHandler assets = StaticHandler.create(webjarsRoot).setCachingEnabled(true);

        Route assetsRoute = router.route(OPENAPI_UI_PATH + "/webjars/*");
        assetsRoute.method(HttpMethod.GET);
        assetsRoute.handler(assets);

        String html = buildIndexHtml(spec);

        Route ui = router.route(OPENAPI_UI_PATH);
        ui.method(HttpMethod.GET);
        ui.handler(ctx -> {
            ctx.response().putHeader("content-type", "text/html;charset=utf-8");
            ctx.end(html);
        });

        Route uiSlash = router.route(OPENAPI_UI_PATH + "/");
        uiSlash.method(HttpMethod.GET);
        uiSlash.handler(ctx -> {
            ctx.response().putHeader("content-type", "text/html;charset=utf-8");
            ctx.end(html);
        });

        platformHttpComponent.addHttpManagementEndpoint(OPENAPI_UI_PATH, "GET", null, "text/html", null);
    }

    private static String buildIndexHtml(String specPath) {
        String prefix = OPENAPI_UI_PATH + "/webjars";
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <title>Camel OpenAPI</title>
                  <link rel="stylesheet" href="%s/swagger-ui.css"/>
                </head>
                <body>
                <div id="swagger-ui"></div>
                <script src="%s/swagger-ui-bundle.js"></script>
                <script src="%s/swagger-ui-standalone-preset.js"></script>
                <script>
                window.onload = function () {
                  window.ui = SwaggerUIBundle({
                    url: "%s",
                    dom_id: '#swagger-ui',
                    deepLinking: true,
                    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
                    layout: "StandaloneLayout"
                  });
                };
                </script>
                </body>
                </html>
                """.formatted(prefix, prefix, prefix, specPath);
    }
}
