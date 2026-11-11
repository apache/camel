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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.quarkiverse.mcp.server.InputResponses;
import io.quarkiverse.mcp.server.Root;
import io.quarkiverse.mcp.server.Roots;
import io.quarkiverse.mcp.server.ToolCallException;
import io.smallrye.mutiny.Uni;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CAMEL-26398: a file tool call that names no directory finds the file in the client's only root, and stays inside it.
 * The roots are the server's {@link Roots} as the tool methods get it, with the client's answer faked: a real round
 * trip needs a client, which this module's tests do not start.
 */
class AuthoringToolsRootsTest {

    private static final String ROUTE = "- route:\n    from:\n      uri: timer:tick\n      steps:\n        - log:\n"
                                        + "            message: hi\n";

    private final AuthoringTools tools = new AuthoringTools();

    @TempDir
    Path workspace;

    @BeforeEach
    void project() throws Exception {
        Files.createDirectories(workspace.resolve("camel"));
        Files.writeString(workspace.resolve("camel/main-route.yaml"), ROUTE, StandardCharsets.UTF_8);
    }

    @Test
    void theOnlyFileRootIsTheDirectory() {
        Path dir = workspace.toAbsolutePath().normalize();
        String uri = dir.toUri().toString();

        assertThat(AuthoringTools.singleRoot(List.of(new Root("project", uri)))).isEqualTo(dir);
        assertThat(AuthoringTools.singleRoot(List.of(new Root("docs", "https://example.com/docs"), new Root("p", uri))))
                .as("only the file: roots count").isEqualTo(dir);
        assertThat(AuthoringTools.singleRoot(List.of(new Root("a", uri), new Root(
                "b", dir.resolve("camel").toUri()
                        .toString()))))
                .as("two roots: none is the default").isNull();
        assertThat(AuthoringTools.singleRoot(List.of())).isNull();
        assertThat(AuthoringTools.singleRoot(List.of(new Root("bad", "file://host/share/x"), new Root("worse", "::"))))
                .isNull();
    }

    @Test
    void aRelativeFileIsReadAndWrittenInTheClientsRoot() throws Exception {
        FakeRoots roots = FakeRoots.answering(List.of(new Root("project", workspace.toUri().toString())));

        JsonObject read = tools.camel_get_files(roots, null, "camel/main-route.yaml");
        assertThat(read.getString("content")).isEqualTo(ROUTE);
        JsonObject validated = tools.camel_validate_source(null, roots, null, "camel/main-route.yaml", null, null);
        assertThat(validated.getBoolean("valid")).as(validated.toJson()).isTrue();
        JsonObject written = tools.camel_write_file(roots, null, "camel/application.properties", "a=b\n", null);
        assertThat(written.getString("status")).as(written.toJson()).isEqualTo("created");
        assertThat(Files.readString(workspace.resolve("camel/application.properties"))).isEqualTo("a=b\n");
        JsonObject edited = tools.camel_edit_file(roots, null, "camel/application.properties", "a=b", "a=c", null, null);
        assertThat(edited.getString("status")).as(edited.toJson()).isEqualTo("edited");
        // a directory the call names inside the root is created by the write
        JsonObject created = tools.camel_write_file(roots, workspace.resolve("config").toString(),
                "application.properties", "a=b\n", null);
        assertThat(created.getString("status")).as(created.toJson()).isEqualTo("created");
    }

    @Test
    void theFileStaysInsideTheRoot() throws Exception {
        FakeRoots roots = FakeRoots.answering(List.of(new Root("project", workspace.resolve("camel").toUri().toString())));
        Path outside = workspace.resolve("other.yaml");
        Files.writeString(outside, ROUTE, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> tools.camel_get_files(roots, null, outside.toString()))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("is not inside the directory");
        assertThatThrownBy(() -> tools.camel_get_files(roots, null, "../other.yaml"))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("file must stay inside the directory");
    }

    @Test
    void theRootsAreAskedForOnlyWhenTheCallNamesNoDirectory() {
        FakeRoots roots = FakeRoots.answering(List.of(new Root("project", workspace.toUri().toString())));

        tools.camel_get_files(roots, workspace.toString(), "camel/main-route.yaml");
        assertThat(roots.asked).hasValue(0);
        tools.camel_get_files(roots, null, workspace.resolve("camel/main-route.yaml").toString());
        assertThat(roots.asked).as("an absolute file inside the root").hasValue(1);
    }

    @Test
    void aClientWithoutRootsIsNotAsked() {
        FakeRoots roots = new FakeRoots(false, () -> {
            throw new AssertionError("a client that did not declare roots is not asked for them");
        });

        assertThatThrownBy(() -> tools.camel_get_files(roots, null, "camel/main-route.yaml"))
                .isInstanceOf(ToolCallException.class).hasMessageStartingWith("directory is required");
        assertThat(roots.asked).hasValue(0);
    }

    @Test
    void aClientThatFailsToListItsRootsGivesNoDefault() {
        FakeRoots failing = new FakeRoots(true, () -> Uni.createFrom().failure(new IllegalStateException("no roots")));

        assertThatThrownBy(() -> tools.camel_write_file(failing, null, "application.properties", "a=b\n", null))
                .isInstanceOf(ToolCallException.class).hasMessageStartingWith("directory is required");
    }

    @Test
    void aClientThatDoesNotAnswerGivesNoDefaultAfterTheWait() {
        FakeRoots silent = new FakeRoots(true, () -> Uni.createFrom().nothing());

        long start = System.nanoTime();
        assertThatThrownBy(() -> tools.camel_get_files(silent, null, "camel/main-route.yaml"))
                .isInstanceOf(ToolCallException.class).hasMessageStartingWith("directory is required");
        assertThat(System.nanoTime() - start).isLessThan(AuthoringTools.ROOTS_WAIT.plusSeconds(10).toNanos());
    }

    /** The roots of a client, as the server asks for them, with the client's answer given. */
    static final class FakeRoots implements Roots {

        final AtomicInteger asked = new AtomicInteger();
        private final boolean supported;
        private final Supplier<Uni<List<Root>>> answer;

        FakeRoots(boolean supported, Supplier<Uni<List<Root>>> answer) {
            this.supported = supported;
            this.answer = answer;
        }

        static FakeRoots answering(List<Root> roots) {
            return new FakeRoots(true, () -> Uni.createFrom().item(roots));
        }

        @Override
        public boolean isSupported() {
            return supported;
        }

        @Override
        public Uni<List<Root>> list() {
            asked.incrementAndGet();
            return answer.get();
        }

        @Override
        public boolean isServerInitiatedRequestSupported() {
            return true;
        }

        @Override
        public InputResponses inputResponses() {
            return null;
        }

        @Override
        public String requestState() {
            return null;
        }
    }
}
