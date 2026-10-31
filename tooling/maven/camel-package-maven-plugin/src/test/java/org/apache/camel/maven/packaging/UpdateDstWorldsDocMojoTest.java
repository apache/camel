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
package org.apache.camel.maven.packaging;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DstWorldModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The unit test of {@link UpdateDstWorldsDocMojo} (CAMEL-26438).
 */
class UpdateDstWorldsDocMojoTest {

    @TempDir
    Path dir;

    @Test
    void testTableFromTheCatalog() throws Exception {
        writeComponent("zoo", "camel-zoo", world("ZooWorld", List.of("ZooDstTest"), List.of("polling | paging\n  and more")));
        writeComponent("foo", "camel-foo", world("FooWorld", List.of("FooADstTest", "FooBDstTest"), List.of("a", "b")),
                world("BarWorld", List.of(), List.of()));
        writeComponent("plain", "camel-plain");

        List<UpdateDstWorldsDocMojo.Row> rows = UpdateDstWorldsDocMojo.findWorlds(dir);
        assertEquals(List.of("BarWorld", "FooWorld", "ZooWorld"), rows.stream().map(r -> r.world().getName()).toList());

        assertEquals("""
                [cols="2,3,3,4,2"]
                |===
                |Component |World (tests) |Fake |What it covers |Issues

                |camel-foo
                |`BarWorld`
                |the fake of BarWorld
                |
                |CAMEL-1

                |camel-foo
                |`FooWorld` (`FooADstTest`, `FooBDstTest`)
                |the fake of FooWorld
                a|* a
                * b
                |CAMEL-1

                |camel-zoo
                |`ZooWorld` (`ZooDstTest`)
                |the fake of ZooWorld
                a|* polling \\| paging and more
                |CAMEL-1
                |===""", UpdateDstWorldsDocMojo.renderTable(rows));
    }

    @Test
    void testUpdateDoc() throws Exception {
        Path doc = dir.resolve("test-dst.adoc");
        Files.writeString(doc, "intro\n\n// dst worlds: START\nold table\n// dst worlds: END\n\nafter\n");
        assertTrue(UpdateDstWorldsDocMojo.updateDoc(doc, "|===\n|new\n|==="));
        assertEquals("intro\n\n// dst worlds: START\n|===\n|new\n|===\n// dst worlds: END\n\nafter\n", Files.readString(doc));
        assertFalse(UpdateDstWorldsDocMojo.updateDoc(doc, "|===\n|new\n|==="));

        assertNull(UpdateDstWorldsDocMojo.replaceTable("no markers", "|==="));
        Files.writeString(doc, "no markers\n");
        assertThrows(MojoExecutionException.class, () -> UpdateDstWorldsDocMojo.updateDoc(doc, "|==="));
    }

    private static DstWorldModel world(String name, List<String> tests, List<String> covers) {
        DstWorldModel world = new DstWorldModel();
        world.setName(name);
        world.setJavaType("org.foo.dst." + name);
        world.setFake("the fake of " + name);
        world.setCovers(covers);
        world.setIssues(List.of("CAMEL-1"));
        world.setTests(tests);
        return world;
    }

    private void writeComponent(String scheme, String artifactId, DstWorldModel... worlds) throws Exception {
        ComponentModel model = new ComponentModel();
        model.setName(scheme);
        model.setScheme(scheme);
        model.setArtifactId(artifactId);
        for (DstWorldModel world : worlds) {
            model.addDstWorld(world);
        }
        Files.writeString(dir.resolve(scheme + ".json"), JsonMapper.createParameterJsonSchema(model));
    }
}
