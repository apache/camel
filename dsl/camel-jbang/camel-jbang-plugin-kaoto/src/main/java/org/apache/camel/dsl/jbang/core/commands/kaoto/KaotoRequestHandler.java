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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.test.TestPlugin;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;

final class KaotoRequestHandler {

    private static final Pattern ROUTE_FILE_PATTERN
            = Pattern.compile(".*\\.camel\\.(yaml|xml)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEST_FILE_PATTERN
            = Pattern.compile(".*\\.citrus(\\.it)?\\.(yaml|xml)$", Pattern.CASE_INSENSITIVE);
    private static final String STATIC_ROOT = "kaoto/src";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";
    private static final String CAMEL_LOG_DIR = ".camel-jbang";
    private static final String CITRUS_LOG_DIR = ".citrus-jbang";

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

    static void registerRoutes(
            Router router, Path sourceDir, CountDownLatch shutdownLatch,
            KaotoLogDispatcher logDispatcher) {
        router.route("/api/*").handler(BodyHandler.create().setBodyLimit(1_048_576));

        router.get("/api/routes").handler(ctx -> handleListFiles(ctx, sourceDir, ROUTE_FILE_PATTERN));
        router.get("/api/routes/log/:filename").handler(ctx -> handleGetLog(ctx, sourceDir, CAMEL_LOG_DIR));
        router.post("/api/routes/run/:filename").handler(ctx -> handleRunRoute(ctx, sourceDir, logDispatcher));
        router.get("/api/routes/:filename").handler(ctx -> handleGetFile(ctx, sourceDir));
        router.put("/api/routes/:filename").handler(ctx -> handlePutFile(ctx, sourceDir));

        router.get("/api/tests").handler(ctx -> handleListTests(ctx, sourceDir));
        router.get("/api/tests/log/:filename").handler(ctx -> handleGetLog(ctx, sourceDir, CITRUS_LOG_DIR));
        router.get("/api/tests/results/:filename").handler(ctx -> handleGetTestResults(ctx, sourceDir));
        router.post("/api/tests/run/:filename").handler(ctx -> handleRunTest(ctx, sourceDir, logDispatcher));
        router.get("/api/tests/:filename").handler(ctx -> handleGetTestFile(ctx, sourceDir));
        router.put("/api/tests/:filename").handler(ctx -> handlePutTestFile(ctx, sourceDir));

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
            collectFiles(testSubDir, TEST_FILE_PATTERN, "", files);
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

    private static void handleRunRoute(RoutingContext ctx, Path sourceDir, KaotoLogDispatcher logDispatcher) {
        String filename = ctx.pathParam("filename");
        if (!isSafeFilename(filename)) {
            sendJsonError(ctx, 400, "Invalid filename");
            return;
        }

        Path filePath = sourceDir.resolve(filename).normalize();
        if (!filePath.startsWith(sourceDir)) {
            sendJsonError(ctx, 403, "Access denied");
            return;
        }

        if (!Files.isRegularFile(filePath)) {
            sendJsonError(ctx, 404, "File not found: " + filename);
            return;
        }

        Path logFile = resolveLogFile(sourceDir, CAMEL_LOG_DIR, filename);
        runCamelCommand(logDispatcher, logFile, "run", filePath.toString());

        JsonObject result = new JsonObject();
        result.put("command", "run");
        result.put("file", filename);
        result.put("started", true);
        sendJson(ctx, 200, result);
    }

    private static void handleRunTest(RoutingContext ctx, Path sourceDir, KaotoLogDispatcher logDispatcher) {
        String filename = ctx.pathParam("filename");
        if (!isSafeFilename(filename)) {
            sendJsonError(ctx, 400, "Invalid filename");
            return;
        }

        Path filePath = resolveTestFile(sourceDir, filename);
        if (filePath == null) {
            sendJsonError(ctx, 404, "File not found: " + filename);
            return;
        }

        Path logFile = resolveLogFile(sourceDir, CITRUS_LOG_DIR, filename);
        runCamelCommand(logDispatcher, logFile, "test", "run", filePath.toString());

        JsonObject result = new JsonObject();
        result.put("command", "test run");
        result.put("file", filename);
        result.put("started", true);
        sendJson(ctx, 200, result);
    }

    private static void handleGetTestResults(RoutingContext ctx, Path sourceDir) {
        String filename = ctx.pathParam("filename");
        if (!isSafeFilename(filename)) {
            sendJsonError(ctx, 400, "Invalid filename");
            return;
        }

        Path resultsDir = sourceDir.resolve(".citrus-jbang/citrus-reports");
        if (!Files.isDirectory(resultsDir)) {
            ctx.response().setStatusCode(204).end();
            return;
        }

        Path testFile = resolveTestFile(sourceDir, filename);
        String testName = testFile != null ? extractTestName(testFile) : null;
        String flowFileName = (testName != null ? testName : resolveBaseNameFromFilename(filename)) + "-flow";
        Path flowFile = findLatestFlowFile(resultsDir, flowFileName);
        if (flowFile == null) {
            ctx.response().setStatusCode(204).end();
            return;
        }

        try {
            String content = Files.readString(flowFile, StandardCharsets.UTF_8);
            ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", JSON_CONTENT_TYPE)
                    .end(content);
        } catch (IOException e) {
            sendJsonError(ctx, 500, "Failed to read test results: " + e.getMessage());
        }
    }

    private static void handleGetTestFile(RoutingContext ctx, Path sourceDir) {
        String filename = ctx.pathParam("filename");
        if (!isSafeFilename(filename)) {
            sendJsonError(ctx, 400, "Invalid filename");
            return;
        }

        Path filePath = resolveTestFile(sourceDir, filename);
        if (filePath == null) {
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

    private static void handlePutTestFile(RoutingContext ctx, Path sourceDir) {
        String filename = ctx.pathParam("filename");
        if (!isSafeFilename(filename)) {
            sendJsonError(ctx, 400, "Invalid filename");
            return;
        }

        Path existing = resolveTestFile(sourceDir, filename);
        Path baseDir = existing != null ? existing.getParent() : sourceDir;
        writeFile(ctx, baseDir, filename);
    }

    private static Path resolveTestFile(Path sourceDir, String filename) {
        Path filePath = sourceDir.resolve(filename).normalize();
        if (filePath.startsWith(sourceDir) && Files.isRegularFile(filePath)) {
            return filePath;
        }
        Path testDir = sourceDir.resolve("test");
        filePath = testDir.resolve(filename).normalize();
        if (filePath.startsWith(testDir) && Files.isRegularFile(filePath)) {
            return filePath;
        }
        return null;
    }

    private static final Pattern YAML_NAME_PATTERN = Pattern.compile("^name:\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern XML_NAME_PATTERN = Pattern.compile("<test\\s[^>]*name=\"([^\"]+)\"");

    static String extractTestName(Path testFile) {
        try {
            String content = Files.readString(testFile, StandardCharsets.UTF_8);
            String fileName = testFile.getFileName().toString().toLowerCase();
            Matcher matcher;
            if (fileName.endsWith(".xml")) {
                matcher = XML_NAME_PATTERN.matcher(content);
            } else {
                matcher = YAML_NAME_PATTERN.matcher(content);
            }
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (IOException e) {
            // fall through
        }
        return null;
    }

    static String resolveBaseNameFromFilename(String filename) {
        if (filename.matches("(?i).*\\.citrus\\.it\\.(yaml|xml)$")) {
            return filename.replaceFirst("(?i)\\.citrus\\.it\\.(yaml|xml)$", "");
        } else if (filename.matches("(?i).*\\.citrus\\.(yaml|xml)$")) {
            return filename.replaceFirst("(?i)\\.citrus\\.(yaml|xml)$", "");
        } else {
            int dot = filename.lastIndexOf('.');
            return dot > 0 ? filename.substring(0, dot) : filename;
        }
    }

    private static Path findLatestFlowFile(Path resultsDir, String flowFilePrefix) {
        Path exact = resultsDir.resolve(flowFilePrefix + ".json");
        if (Files.isRegularFile(exact)) {
            return exact;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(resultsDir,
                path -> path.getFileName().toString().startsWith(flowFilePrefix)
                        && path.getFileName().toString().endsWith(".json"))) {
            Path latest = null;
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    if (latest == null
                            || entry.getFileName().toString().compareTo(latest.getFileName().toString()) > 0) {
                        latest = entry;
                    }
                }
            }
            return latest;
        } catch (IOException e) {
            return null;
        }
    }

    private static void handleGetLog(RoutingContext ctx, Path sourceDir, String logDir) {
        String filename = ctx.pathParam("filename");
        if (!isSafeFilename(filename)) {
            sendJsonError(ctx, 400, "Invalid filename");
            return;
        }

        Path logFile = resolveLogFile(sourceDir, logDir, filename);
        if (!Files.isRegularFile(logFile)) {
            ctx.response().setStatusCode(204).end();
            return;
        }

        try {
            String content = Files.readString(logFile, StandardCharsets.UTF_8);
            ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "text/plain; charset=UTF-8")
                    .end(content);
        } catch (IOException e) {
            sendJsonError(ctx, 500, "Failed to read log: " + e.getMessage());
        }
    }

    static Path resolveLogFile(Path sourceDir, String logDir, String filename) {
        int dot = filename.lastIndexOf('.');
        String baseName = dot > 0 ? filename.substring(0, dot) : filename;
        return sourceDir.resolve(logDir).resolve(baseName + ".log");
    }

    static void runCamelCommand(KaotoLogDispatcher logDispatcher, Path logFile, String... args) {
        Thread thread = new Thread(() -> {
            BufferedWriter logWriter = null;
            Consumer<String> listener = null;
            if (logFile != null) {
                try {
                    Files.createDirectories(logFile.getParent());
                    logWriter = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    BufferedWriter writer = logWriter;
                    listener = message -> {
                        synchronized (writer) {
                            try {
                                writer.write(message);
                            } catch (IOException e) {
                                // ignore write errors
                            }
                        }
                    };
                    logDispatcher.addListener(listener);
                } catch (IOException e) {
                    logWriter = null;
                }
            }
            try {
                CamelJBangMain main = initializeMain();
                CommandLine commandLine = main.newCommandLine();
                commandLine.execute(args);
            } finally {
                if (listener != null) {
                    logDispatcher.removeListener(listener);
                }
                if (logWriter != null) {
                    try {
                        logWriter.flush();
                    } catch (IOException e) {
                        // ignore
                    }

                    try {
                        logWriter.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        });
        thread.setName("kaoto-" + String.join("-", args));
        thread.setDaemon(true);
        thread.start();
    }

    private static CamelJBangMain initializeMain() {
        CamelJBangMain camelJBangMain = new CamelJBangMain() {
            @Override
            public void quit(int exitCode) {
            }

            @Override
            public void postAddCommands(CommandLine commandLine, String[] args) {
                TestPlugin plugin = new TestPlugin();
                plugin.customize(commandLine, this);
            }
        };

        camelJBangMain.setDiscoverPlugins(false);

        return camelJBangMain;
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
