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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26398: the file tools (camel_validate_source, camel_get_files, camel_edit_file, camel_write_file) take the file
 * the way a model names it: an absolute file inside the directory, a relative file in the client's root, a file in a
 * subdirectory the answer names. The file always stays inside the project directory, symbolic links resolved: an
 * absolute path never reads or writes another file of the disk.
 */
class AuthoringToolsFilePathsTest {

    private static final String ROUTE = """
            - route:
                id: orders
                from:
                  uri: timer:tick
                  steps:
                    - log:
                        message: "${body}"
            """;

    @TempDir
    Path workspace;

    private Path routes;

    @BeforeEach
    void project() throws IOException {
        // the layout of the eval: the routes in camel/ under the directory the agent works in
        routes = Files.createDirectories(workspace.resolve("camel")).resolve("main-route.yaml");
        Files.writeString(routes, ROUTE, StandardCharsets.UTF_8);
    }

    private static JsonObject call(ToolContext ctx, String tool, Map<String, String> args) {
        Object result = ToolRegistry.execute(tool, ctx, args);
        return Jsoner.deserialize(String.valueOf(result), new JsonObject());
    }

    private static String error(ToolContext ctx, String tool, Map<String, String> args) {
        try {
            ToolRegistry.execute(tool, ctx, args);
        } catch (ToolExecutionException e) {
            return e.getMessage();
        }
        throw new AssertionError(tool + " " + args + " did not fail");
    }

    @Test
    void anAbsoluteFileInsideTheDirectoryIsMadeRelativeToIt() throws IOException {
        Map<String, String> args = Map.of("directory", workspace.toString(), "file", routes.toString());
        JsonObject validated = call(new ToolContext(), "camel_validate_source", args);
        assertThat(validated.getBoolean("valid")).as(validated.toJson()).isTrue();
        assertThat(validated.getString("file")).isEqualTo("camel/main-route.yaml");

        assertThat(call(new ToolContext(), "camel_get_files", args).getString("file")).isEqualTo("camel/main-route.yaml");

        JsonObject edited = call(new ToolContext(), "camel_edit_file", Map.of("directory", workspace.toString(), "file",
                routes.toString(), "find", "id: orders", "replace", "id: order-intake"));
        assertThat(edited.getString("status")).as(edited.toJson()).isEqualTo("edited");
        assertThat(Files.readString(routes)).contains("id: order-intake");

        JsonObject written = call(new ToolContext(), "camel_write_file",
                Map.of("directory", workspace.toString(), "file", workspace.resolve("application.properties").toString(),
                        "content", "camel.main.name=orders\n"));
        assertThat(written.getString("status")).as(written.toJson()).isEqualTo("created");
        assertThat(written.getString("file")).isEqualTo("application.properties");
        assertThat(Files.readString(workspace.resolve("application.properties"))).isEqualTo("camel.main.name=orders\n");
    }

    @Test
    void anAbsoluteFileInsideTheDirectoryOnlyThroughASymbolicLinkIsInsideIt() throws IOException {
        // on macOS a temporary directory is under /var, a symbolic link to /private/var, and a client may name either
        Path real = workspace.toRealPath();
        Map<String, String> args = Map.of("directory", real.toString(), "file", routes.toString());

        assertThat(call(new ToolContext(), "camel_get_files", args).getString("file")).isEqualTo("camel/main-route.yaml");
    }

    @Test
    void anAbsoluteFileOutsideTheDirectoryIsRefused() throws IOException {
        Path project = Files.createDirectories(workspace.resolve("project"));
        Map<String, String> args = Map.of("directory", project.toString(), "file", routes.toString());

        assertThat(error(new ToolContext(), "camel_get_files", args))
                .isEqualTo("file " + routes + " is not inside the directory " + project
                           + ": pass file relative to the directory, or the absolute path of a file inside it");
        assertThat(error(new ToolContext(), "camel_validate_source", args)).contains("is not inside the directory");
        assertThat(error(new ToolContext(), "camel_write_file", Map.of("directory", project.toString(), "file",
                workspace.resolve("new.yaml").toString(), "content", ROUTE))).contains("is not inside the directory");
        assertThat(workspace.resolve("new.yaml")).doesNotExist();
    }

    @Test
    void anAbsoluteFileNeverNamesTheDirectory() {
        // the directory of the file would let a call read any file of the disk
        for (String tool : new String[] { "camel_validate_source", "camel_get_files" }) {
            assertThat(error(new ToolContext(), tool, Map.of("file", routes.toString())))
                    .isEqualTo("directory is required: pass directory, the absolute project directory with the source"
                               + " files, and file relative to it (an absolute file must be inside it)");
        }
        Path file = workspace.resolve("camel/application.properties");
        assertThat(error(new ToolContext(), "camel_write_file", Map.of("file", file.toString(), "content", "a=b\n")))
                .startsWith("directory is required");
        assertThat(file).doesNotExist();
    }

    @Test
    void aCallThatNamesNoPlaceSaysWhatToPass() {
        assertThat(error(new ToolContext(), "camel_validate_source", Map.of("file", "camel/main-route.yaml")))
                .isEqualTo("directory is required: pass directory, the absolute project directory with the source"
                           + " files, and file relative to it");
        assertThat(error(new ToolContext(), "camel_get_files", Map.of())).startsWith("directory is required: pass"
                                                                                     + " directory");
    }

    @Test
    void aRelativeFileWithoutDirectoryIsInTheRoot() throws IOException {
        ToolContext ctx = new ToolContext();
        AtomicInteger asked = new AtomicInteger();
        ctx.setRootDirectory(() -> {
            asked.incrementAndGet();
            return workspace;
        });

        JsonObject validated = call(ctx, "camel_validate_source", Map.of("file", "camel/main-route.yaml"));
        assertThat(validated.getBoolean("valid")).as(validated.toJson()).isTrue();
        JsonObject written = call(ctx, "camel_write_file",
                Map.of("file", "camel/application.properties", "content", "camel.main.name=orders\n"));
        assertThat(written.getString("status")).as(written.toJson()).isEqualTo("created");
        assertThat(Files.readString(workspace.resolve("camel/application.properties")))
                .isEqualTo("camel.main.name=orders\n");
        // asking the client is a round trip: once per context
        assertThat(asked).hasValue(1);
    }

    @Test
    void anAbsoluteFileIsTakenInsideTheRootOnly() {
        ToolContext ctx = new ToolContext();
        ctx.setRootDirectory(() -> workspace.resolve("camel"));

        assertThat(call(ctx, "camel_validate_source", Map.of("file", routes.toString())).getString("file"))
                .isEqualTo("main-route.yaml");
        assertThat(error(ctx, "camel_get_files", Map.of("file", workspace.resolve("other.yaml").toString())))
                .contains("is not inside the directory " + workspace.resolve("camel"));
    }

    @Test
    void theRootIsNotAskedForWhenTheCallNamesTheDirectory() {
        ToolContext ctx = new ToolContext();
        AtomicInteger asked = new AtomicInteger();
        ctx.setRootDirectory(() -> {
            asked.incrementAndGet();
            return workspace;
        });

        call(ctx, "camel_get_files", Map.of("directory", workspace.toString(), "file", "camel/main-route.yaml"));

        assertThat(asked).hasValue(0);
    }

    @Test
    void theDefaultDirectoryComesBeforeTheRoot() throws IOException {
        // the TUI's selected integration, or camel ask's project, is where a relative file is
        Path selected = Files.createDirectories(workspace.resolve("selected"));
        Files.writeString(selected.resolve("main-route.yaml"), ROUTE);
        ToolContext ctx = new ToolContext();
        ctx.setDefaultDirectory(selected);
        ctx.setRootDirectory(() -> {
            throw new AssertionError("the root is not asked for");
        });

        JsonObject read = call(ctx, "camel_get_files", Map.of("file", "main-route.yaml"));

        assertThat(read.getString("directory")).isEqualTo(selected.toString());
        assertThat(error(ctx, "camel_get_files", Map.of("file", routes.toString())))
                .contains("is not inside the directory " + selected);
    }

    @Test
    void aWriteCreatesAMissingDirectoryInsideTheRoot() throws IOException {
        ToolContext ctx = new ToolContext();
        ctx.setRootDirectory(() -> workspace);
        Path named = workspace.resolve("camel-new");

        JsonObject written = call(ctx, "camel_write_file",
                Map.of("directory", named.toString(), "file", "application.properties", "content", "a=b\n"));

        assertThat(written.getString("status")).as(written.toJson()).isEqualTo("created");
        assertThat(Files.readString(named.resolve("application.properties"))).isEqualTo("a=b\n");
    }

    @Test
    void aWriteDoesNotCreateADirectoryOutsideAProject() {
        // no root: the existing directory to pass, with the subdirectory in the file, which a write creates
        Path named = workspace.resolve("camel-new");
        String message = error(new ToolContext(), "camel_write_file",
                Map.of("directory", named.toString(), "file", "application.properties", "content", "a=b\n"));
        assertThat(message).isEqualTo("No such directory: " + named + "; pass the existing project directory as"
                                      + " directory and the subdirectory in file, which is created: {\"directory\":\""
                                      + workspace + "\",\"file\":\"camel-new/application.properties\"}");
        assertThat(named).doesNotExist();

        // a root elsewhere is not a project the directory is in
        ToolContext ctx = new ToolContext();
        ctx.setRootDirectory(() -> workspace.resolve("camel"));
        assertThat(error(ctx, "camel_write_file",
                Map.of("directory", named.toString(), "file", "application.properties", "content", "a=b\n")))
                .startsWith("No such directory: " + named);
        assertThat(named).doesNotExist();
    }

    @Test
    void anInvalidWriteCreatesNoDirectory() {
        ToolContext ctx = new ToolContext();
        ctx.setRootDirectory(() -> workspace);
        Path named = workspace.resolve("camel-new");

        JsonObject refused = call(ctx, "camel_write_file", Map.of("directory", named.toString(), "file",
                "routes.yaml", "content", ROUTE.replace("message:", "mesage:")));

        assertThat(refused.getString("status")).as(refused.toJson()).isEqualTo("invalid");
        assertThat(named).doesNotExist();
    }

    @Test
    void onlyTheWriteCreatesADirectoryAndNeverTheRoot() {
        ToolContext ctx = new ToolContext();
        ctx.setRootDirectory(() -> workspace);
        Path missing = workspace.resolve("missing");
        assertThat(error(ctx, "camel_get_files", Map.of("directory", missing.toString(), "file", "a.yaml")))
                .isEqualTo("No such directory: " + missing);
        assertThat(error(ctx, "camel_edit_file",
                Map.of("directory", missing.toString(), "file", "a.yaml", "find", "a", "replace", "b")))
                .isEqualTo("No such directory: " + missing);

        ToolContext rooted = new ToolContext();
        rooted.setRootDirectory(() -> missing);
        assertThat(error(rooted, "camel_write_file", Map.of("file", "a.properties", "content", "a=b\n")))
                .isEqualTo("No such directory: " + missing);
        assertThat(missing).doesNotExist();
    }

    @Test
    void aFileNotFoundNamesTheFileOfThatNameInASubdirectory() throws IOException {
        // the copy under target/ is build output, not the one to name
        Files.createDirectories(workspace.resolve("target/classes"));
        Files.writeString(workspace.resolve("target/classes/main-route.yaml"), ROUTE);
        Map<String, String> args = Map.of("directory", workspace.toString(), "file", "main-route.yaml");

        assertThat(error(new ToolContext(), "camel_validate_source", args))
                .isEqualTo("No such file in the directory " + workspace + ": main-route.yaml; camel/main-route.yaml"
                           + " exists: pass file camel/main-route.yaml");
        assertThat(error(new ToolContext(), "camel_get_files", args))
                .endsWith("; camel/main-route.yaml exists: pass file camel/main-route.yaml");
        // an edit that would otherwise be told to write a new file, a second copy of it
        assertThat(error(new ToolContext(), "camel_edit_file",
                Map.of("directory", workspace.toString(), "file", "main-route.yaml", "find", "id: orders", "replace",
                        "id: x")))
                .isEqualTo("main-route.yaml does not exist, but camel/main-route.yaml exists: pass file"
                           + " camel/main-route.yaml (camel_write_file writes a new file)");
    }

    @Test
    void theSourcePassedAsTheFileIsToldToGoInContent() {
        String message = error(new ToolContext(), "camel_validate_source",
                Map.of("directory", workspace.toString(), "file", ROUTE));
        assertThat(message).isEqualTo("file has several lines, so it is not a file name: file names the file, e.g."
                                      + " routes.camel.yaml, and the source goes in content");

        // the edit has no content: find and replace say what to change
        assertThat(error(new ToolContext(), "camel_edit_file",
                Map.of("directory", workspace.toString(), "file", ROUTE, "find", "a", "replace", "b")))
                .doesNotContain("content");
    }

    @Test
    void aFileStillStaysInsideItsDirectory() throws IOException {
        Files.writeString(workspace.resolve("outside.yaml"), ROUTE);
        ToolContext rooted = new ToolContext();
        rooted.setRootDirectory(() -> workspace.resolve("camel"));
        assertThat(error(rooted, "camel_write_file", Map.of("file", "../escaped.yaml", "content", ROUTE)))
                .contains("file must stay inside the directory");
        assertThat(error(new ToolContext(), "camel_get_files",
                Map.of("directory", workspace.resolve("camel").toString(), "file", "sub/../../outside.yaml")))
                .contains("file must stay inside the directory");
        assertThat(workspace.resolve("escaped.yaml")).doesNotExist();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aSymbolicLinkInTheProjectDoesNotLeadOutOfIt() throws IOException {
        Path outside = Files.createDirectories(workspace.resolve("outside"));
        Files.writeString(outside.resolve("secret.yaml"), ROUTE);
        Path project = Files.createDirectories(workspace.resolve("project"));
        Files.createSymbolicLink(project.resolve("shared"), outside);
        Files.createSymbolicLink(project.resolve("linked.yaml"), outside.resolve("secret.yaml"));
        Files.createSymbolicLink(project.resolve("dangling.yaml"), outside.resolve("created.yaml"));
        ToolContext ctx = new ToolContext();
        ctx.setRootDirectory(() -> project);

        for (String file : new String[] { "shared/secret.yaml", "linked.yaml" }) {
            assertThat(error(ctx, "camel_get_files", Map.of("file", file)))
                    .isEqualTo("file must stay inside the directory " + project + ": " + file
                               + " (a symbolic link leads out of it)");
            assertThat(error(ctx, "camel_validate_source", Map.of("file", file))).contains("a symbolic link leads out");
        }
        assertThat(error(ctx, "camel_get_files", Map.of("file", project.resolve("shared/secret.yaml").toString())))
                .contains("is not inside the directory " + project);
        // a write through a link, also one to nothing yet, would create or change the file outside
        assertThat(error(ctx, "camel_write_file", Map.of("file", "shared/new.yaml", "content", ROUTE)))
                .contains("a symbolic link leads out");
        assertThat(error(ctx, "camel_write_file", Map.of("file", "dangling.yaml", "content", ROUTE)))
                .contains("a symbolic link leads out");
        assertThat(error(ctx, "camel_write_file",
                Map.of("directory", project.resolve("shared/sub").toString(), "file", "new.yaml", "content", ROUTE)))
                .startsWith("No such directory");
        assertThat(outside.resolve("new.yaml")).doesNotExist();
        assertThat(outside.resolve("created.yaml")).doesNotExist();
        assertThat(outside.resolve("sub")).doesNotExist();
    }

    @Test
    void contentTheSameAsTheAbsoluteFileOnDiskIsNoted() {
        JsonObject validated = call(new ToolContext(), "camel_validate_source",
                Map.of("directory", workspace.toString(), "file", routes.toString(), "content", ROUTE));

        assertThat(validated.getBoolean("sameAsFile")).as(validated.toJson()).isTrue();
    }
}
