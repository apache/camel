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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.camel.tooling.model.DstWorldModel;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The unit test of {@link DstWorldsHelper} and of the check of the worlds in {@link EndpointSchemaGeneratorMojo}
 * (CAMEL-26438).
 */
class DstWorldsHelperTest {

    private static final String WORLD = """
            package org.foo.dst;

            import org.apache.camel.test.dst.Dst;
            import org.apache.camel.test.dst.DstWorld;

            /** The world. */
            @DstWorld(component = "foo",
                      fake = "FakeFoo: a queue, " + "with \\"quotes\\" and a \\u00e9",
                      covers = { "polling", "the extender" + " of locks", },
                      faults = "receive fails",
                      issues = { "CAMEL-1", "CAMEL-2" })
            final class FooWorld {
                FooWorld(Dst dst) {
                }
            }
            """;

    private static final String TEST = """
            package org.foo.dst;

            class %s {
                @DstTest(seeds = 10)
                void test(Dst dst) {
                    new FooWorld(dst).run();
                }
            }
            """;

    @TempDir
    Path dir;

    @Test
    void testParseStrings() {
        assertEquals(List.of("a"), DstWorldsHelper.parseStrings("\"a\""));
        assertEquals(List.of("ab c"), DstWorldsHelper.parseStrings("\"a\" + \"b\"\n  + \" c\""));
        assertEquals(List.of(), DstWorldsHelper.parseStrings("{}"));
        assertEquals(List.of("a", "bc"), DstWorldsHelper.parseStrings("{ \"a\", \"b\" + \"c\", }"));
        assertEquals(List.of("say \"hi\"\t\\ \u00e9"),
                DstWorldsHelper.parseStrings("\"say \\\"hi\\\"\\t\\\\ \\u00e9\""));
        assertEquals(List.of("a, b", "{c}"), DstWorldsHelper.parseStrings("{\"a, b\",\"{c}\"}"));
    }

    @Test
    void testParseStringsRejectsWhatIsNotALiteral() {
        assertThrows(IllegalArgumentException.class, () -> DstWorldsHelper.parseStrings("FAKE"));
        assertThrows(IllegalArgumentException.class, () -> DstWorldsHelper.parseStrings("\"a\" + FAKE"));
        assertThrows(IllegalArgumentException.class, () -> DstWorldsHelper.parseStrings("{ \"a\" \"b\" }"));
        assertThrows(IllegalArgumentException.class, () -> DstWorldsHelper.parseStrings("\"\"\"\n text\n \"\"\""));
        assertThrows(IllegalArgumentException.class, () -> DstWorldsHelper.parseStrings("\"open"));
        assertThrows(IllegalArgumentException.class, () -> DstWorldsHelper.parseStrings("\"a\".trim()"));
    }

    @Test
    void testFindWorldAndItsTests() throws IOException {
        write("org/foo/dst/FooWorld.java", WORLD);
        write("org/foo/dst/FooDstTest.java", TEST.formatted("FooDstTest"));
        write("org/foo/dst/FooOtherDstTest.java", TEST.formatted("FooOtherDstTest"));
        // not a DST test, and a DST test of another world
        write("org/foo/dst/FooHelper.java", "package org.foo.dst;\nclass FooHelper { FooWorld world; }\n");
        write("org/foo/dst/BarDstTest.java", TEST.formatted("BarDstTest").replace("FooWorld", "BarWorld"));
        // a class that only mentions the annotation, and one with another annotation of that name
        write("org/foo/Notes.java", "package org.foo;\n/** See DstWorld. */\nclass Notes {\n}\n");
        write("org/foo/Other.java", "package org.foo;\nimport org.other.DstWorld;\n@DstWorld(component = \"foo\")\n"
                                    + "class Other {\n}\n");

        Map<String, List<DstWorldModel>> worlds = DstWorldsHelper.findDstWorlds(List.of(dir, dir.resolve("missing")));
        assertEquals(List.of("foo"), List.copyOf(worlds.keySet()));
        assertEquals(1, worlds.get("foo").size());
        DstWorldModel world = worlds.get("foo").get(0);
        assertEquals("FooWorld", world.getName());
        assertEquals("org.foo.dst.FooWorld", world.getJavaType());
        assertEquals("FakeFoo: a queue, with \"quotes\" and a \u00e9", world.getFake());
        assertEquals(List.of("polling", "the extender of locks"), world.getCovers());
        assertEquals(List.of("receive fails"), world.getFaults());
        assertEquals(List.of(), world.getInvariants());
        assertEquals(List.of("CAMEL-1", "CAMEL-2"), world.getIssues());
        assertEquals(List.of("FooDstTest", "FooOtherDstTest"), world.getTests());
    }

    @Test
    void testWorldsAreSortedByName() throws IOException {
        write("org/foo/dst/FooWorld.java", WORLD);
        write("org/foo/dst2/AaaWorld.java", WORLD.replace("org.foo.dst;", "org.foo.dst2;").replace("FooWorld", "AaaWorld"));
        List<DstWorldModel> worlds = DstWorldsHelper.findDstWorlds(List.of(dir)).get("foo");
        assertEquals(List.of("AaaWorld", "FooWorld"), worlds.stream().map(DstWorldModel::getName).toList());
    }

    @Test
    void testInvalidWorlds() throws IOException {
        write("org/foo/dst/FooWorld.java", WORLD.replace("faults = \"receive fails\"", "faults = FAULTS"));
        IllegalArgumentException e
                = assertThrows(IllegalArgumentException.class, () -> DstWorldsHelper.findDstWorlds(List.of(dir)));
        assertTrue(e.getMessage().contains("faults must be string literals"), e.getMessage());

        write("org/foo/dst/FooWorld.java", WORLD.replace("component = \"foo\"", "component = \" \""));
        e = assertThrows(IllegalArgumentException.class, () -> DstWorldsHelper.findDstWorlds(List.of(dir)));
        assertTrue(e.getMessage().contains("needs a non-empty component"), e.getMessage());

        write("org/foo/dst/FooWorld.java", WORLD);
        write("org/foo/dst2/FooWorld.java", WORLD.replace("org.foo.dst;", "org.foo.dst2;"));
        e = assertThrows(IllegalArgumentException.class, () -> DstWorldsHelper.findDstWorlds(List.of(dir)));
        assertTrue(e.getMessage().contains("the same simple name"), e.getMessage());
    }

    @Test
    void testWorldNotOnATopLevelClassFails() throws IOException {
        // a world on a record, an interface, an enum or a nested class would be left out of the catalog
        String head = WORLD.substring(0, WORLD.indexOf("final class FooWorld"));
        for (String text : List.of(head + "record FooWorld(String a) {\n}\n", head + "interface FooWorld {\n}\n",
                head + "enum FooWorld {\n    A\n}\n",
                "package org.foo.dst;\nimport org.apache.camel.test.dst.DstWorld;\nclass Outer {\n"
                                                      + head.substring(head.indexOf("/**"))
                                                      + "static class FooWorld {\n}\n}\n")) {
            write("org/foo/dst/FooWorld.java", text);
            IllegalArgumentException e
                    = assertThrows(IllegalArgumentException.class, () -> DstWorldsHelper.findDstWorlds(List.of(dir)));
            assertTrue(e.getMessage().contains("must be on the top-level class"), e.getMessage());
        }
        write("org/foo/dst/FooWorld.java", "import org.apache.camel.test.dst.DstWorld;\n@DstWorld(component = \"foo\")\n");
        IllegalArgumentException e
                = assertThrows(IllegalArgumentException.class, () -> DstWorldsHelper.findDstWorlds(List.of(dir)));
        assertTrue(e.getMessage().contains("FooWorld.java"), e.getMessage());
    }

    @Test
    void testOtherTestSourcesDoNotFailTheBuild() throws IOException {
        // a test file in another encoding, a directory named like a source, the same root twice, and sources that only
        // mention the annotation
        Path latin1 = dir.resolve("org/foo/Latin1Test.java");
        Files.createDirectories(latin1.getParent());
        Files.write(latin1,
                "package org.foo;\n// caf\u00e9 DstWorld\nclass Latin1Test {\n}\n".getBytes(StandardCharsets.ISO_8859_1));
        Files.createDirectories(dir.resolve("org/foo/Odd.java"));
        // sources that only mention the annotation of camel-test-dst, in a comment or a string
        write("org/foo/dst/Mention1Test.java", "package org.foo.dst;\nimport org.apache.camel.test.dst.*;\n"
                                               + "/** A world has {@code @DstWorld}. */\nclass Mention1Test {\n}\n");
        write("org/foo/dst/Mention2Test.java", "package org.foo.dst;\n// see @org.apache.camel.test.dst.DstWorld\n"
                                               + "class Mention2Test {\n}\n");
        write("org/apache/camel/test/dst/Mention3Test.java", "package org.apache.camel.test.dst;\n"
                                                             + "/** Not a @DstWorld. */\nclass Mention3Test {\n}\n");
        write("org/foo/dst/Mention4Test.java", "package org.foo.dst;\nclass Mention4Test {\n    String s = \""
                                               + "import org.apache.camel.test.dst.DstWorld; @DstWorld(component = x)\";\n}\n");
        write("org/foo/dst/FooWorld.java", WORLD);
        assertEquals(List.of("FooWorld"),
                DstWorldsHelper.findDstWorlds(List.of(dir, dir.resolve("org/..")))
                        .get("foo").stream().map(DstWorldModel::getName).toList());
    }

    @Test
    void testMojoAddsTheWorldsOfItsSchemeAndFailsOnAnUnknownScheme() throws Exception {
        write("org/foo/dst/FooWorld.java", WORLD);
        EndpointSchemaGeneratorMojo mojo = new EndpointSchemaGeneratorMojo();
        mojo.project = new MavenProject();
        mojo.testSourceRoots = List.of(dir);
        assertEquals(List.of("FooWorld"), mojo.findDstWorlds("foo").stream().map(DstWorldModel::getName).toList());
        assertEquals(List.of(), mojo.findDstWorlds("foos"));
        mojo.checkDstWorlds();

        EndpointSchemaGeneratorMojo other = new EndpointSchemaGeneratorMojo();
        other.project = new MavenProject();
        other.testSourceRoots = List.of(dir);
        assertEquals(List.of(), other.findDstWorlds("bar"));
        MojoExecutionException e = assertThrows(MojoExecutionException.class, other::checkDstWorlds);
        assertTrue(e.getMessage().contains("org.foo.dst.FooWorld names the component foo"), e.getMessage());

        // a module without components
        EndpointSchemaGeneratorMojo none = new EndpointSchemaGeneratorMojo();
        none.project = new MavenProject();
        none.testSourceRoots = List.of(dir);
        assertThrows(MojoExecutionException.class, none::checkDstWorlds);
    }

    private Path write(String name, String text) throws IOException {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        return file;
    }
}
