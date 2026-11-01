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
package org.apache.camel.dsl.jbang.core.commands.kaoto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

final class KaotoRequestHandler {

    private static final Pattern ROUTE_FILE_PATTERN
            = Pattern.compile(".*\\.camel\\.(yaml|xml)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEST_FILE_PATTERN
            = Pattern.compile(".*\\.citrus(\\.it)?\\.(yaml|xml)$", Pattern.CASE_INSENSITIVE);
    private static final String STATIC_ROOT = "kaoto/src";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=UTF-8"),
            Map.entry("js", "application/javascript; charset=UTF-8"),
            Map.entry("mjs", "application/javascript; charset=UTF-8"),
            Map.entry("css", "text/css; charset=UTF-8"),
            Map.entry("json", "application/json; charset=UTF-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("txt", "text/plain; charset=UTF-8"));

    private KaotoRequestHandler() {
    }

    static void registerRoutes(Router router, Path sourceDir, CountDownLatch shutdownLatch) {
        router.route("/api/*").handler(BodyHandler.create().setBodyLimit(1_048_576));

        router.get("/api/routes").handler(ctx -> handleListFiles(ctx, sourceDir, ROUTE_FILE_PATTERN));
        router.get("/api/routes/:filename").handler(ctx -> handleGetFile(ctx, sourceDir));
        router.put("/api/routes/:filename").handler(ctx -> handlePutFile(ctx, sourceDir));

        router.get("/api/tests").handler(ctx -> handleListTests(ctx, sourceDir));
        router.get("/api/tests/test/:filename").handler(ctx -> handleGetFile(ctx, sourceDir.resolve("test")));
        router.get("/api/tests/:filename").handler(ctx -> handleGetFile(ctx, sourceDir));
        router.put("/api/tests/test/:filename").handler(ctx -> handlePutFile(ctx, sourceDir.resolve("test")));
        router.put("/api/tests/:filename").handler(ctx -> handlePutFile(ctx, sourceDir));

        router.post("/api/shutdown").handler(ctx -> {
            sendJson(ctx, 200, Map.of("shutdown", true));
            shutdownLatch.countDown();
        });

        router.get("/*").handler(ctx -> handleStaticFile(ctx));
    }

    private static void handleListFiles(RoutingContext ctx, Path dir, Pattern pattern) {
        JsonArray files = new JsonArray();
        collectFiles(dir, pattern, "", files);
        sendJson(ctx, 200, files);
    }

    private static void handleListTests(RoutingContext ctx, Path sourceDir) {
        JsonArray files = new JsonArray();
        collectFiles(sourceDir, TEST_FILE_PATTERN, "", files);
        Path testSubDir = sourceDir.resolve("test");
        if (Files.isDirectory(testSubDir)) {
            collectFiles(testSubDir, TEST_FILE_PATTERN, "test/", files);
        }
        sendJson(ctx, 200, files);
    }

    private static void handleGetFile(RoutingContext ctx, Path baseDir) {
        String filename = ctx.pathParam("filename");
        if (!isSafeFilename(filename)) {
            sendJsonError(ctx, 400, "Invalid filename");
            return;
        }

        Path filePath = baseDir.resolve(filename).normalize();
        if (!filePath.startsWith(baseDir)) {
            sendJsonError(ctx, 403, "Access denied");
            return;
        }

        if (!Files.isRegularFile(filePath)) {
            sendJsonError(ctx, 404, "File not found: " + filename);
            return;
        }

        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            JsonObject result = new JsonObject();
            result.put("name", filename);
            result.put("content", content);
            sendJson(ctx, 200, result);
        } catch (IOException e) {
            sendJsonError(ctx, 500, "Failed to read file: " + e.getMessage());
        }
    }

    private static void handlePutFile(RoutingContext ctx, Path baseDir) {
        String filename = ctx.pathParam("filename");
        writeFile(ctx, baseDir, filename);
    }

    private static void writeFile(RoutingContext ctx, Path baseDir, String filename) {
        if (!isSafeFilename(filename)) {
            sendJsonError(ctx, 400, "Invalid filename");
            return;
        }

        Path filePath = baseDir.resolve(filename).normalize();
        if (!filePath.startsWith(baseDir)) {
            sendJsonError(ctx, 403, "Access denied");
            return;
        }

        try {
            JsonObject json = (JsonObject) Jsoner.deserialize(ctx.body().asString());
            String content = json.getString("content");
            if (content == null) {
                sendJsonError(ctx, 400, "Missing 'content' field");
                return;
            }

            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content, StandardCharsets.UTF_8);
            JsonObject result = new JsonObject();
            result.put("name", filename);
            result.put("saved", true);
            sendJson(ctx, 200, result);
        } catch (Exception e) {
            sendJsonError(ctx, 500, "Failed to save file: " + e.getMessage());
        }
    }

    private static void handleStaticFile(RoutingContext ctx) {
        String path = ctx.request().path();
        String resourcePath = path;
        if ("/".equals(resourcePath)) {
            resourcePath = "/index.html";
        }

        if (resourcePath.contains("..") || resourcePath.contains("\\")) {
            ctx.response().setStatusCode(403).end();
            return;
        }

        String classpathResource = STATIC_ROOT + resourcePath;
        byte[] content = loadClasspathResource(classpathResource);
        if (content != null) {
            String contentType = guessContentType(resourcePath);
            ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", contentType)
                    .end(Buffer.buffer(content));
            return;
        }

        // SPA fallback: serve index.html for unmatched paths (client-side routing)
        content = loadClasspathResource(STATIC_ROOT + "/index.html");
        if (content != null) {
            ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "text/html; charset=UTF-8")
                    .end(Buffer.buffer(content));
            return;
        }

        ctx.response().setStatusCode(404).end();
    }

    private static void collectFiles(Path dir, Pattern pattern, String prefix, JsonArray target) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry) && pattern.matcher(entry.getFileName().toString()).matches()) {
                    JsonObject fileInfo = new JsonObject();
                    fileInfo.put("name", prefix + entry.getFileName().toString());
                    fileInfo.put("size", Files.size(entry));
                    fileInfo.put("lastModified", Files.getLastModifiedTime(entry).toMillis());
                    target.add(fileInfo);
                }
            }
        } catch (IOException e) {
            // directory not readable, skip
        }
    }

    private static byte[] loadClasspathResource(String path) {
        try (InputStream is = KaotoRequestHandler.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null) {
                return is.readAllBytes();
            }
        } catch (IOException e) {
            // resource not readable
        }
        return null;
    }

    private static String guessContentType(String path) {
        int dot = path.lastIndexOf('.');
        if (dot >= 0) {
            String ext = path.substring(dot + 1).toLowerCase();
            return CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
        }
        return "application/octet-stream";
    }

    private static boolean isSafeFilename(String filename) {
        return filename != null
                && !filename.isEmpty()
                && !filename.contains("/")
                && !filename.contains("\\")
                && !filename.contains("..");
    }

    private static void sendJson(RoutingContext ctx, int statusCode, Object json) {
        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", JSON_CONTENT_TYPE)
                .end(Jsoner.serialize(json));
    }

    private static void sendJsonError(RoutingContext ctx, int statusCode, String message) {
        JsonObject error = new JsonObject();
        error.put("error", message);
        sendJson(ctx, statusCode, error);
    }
}
