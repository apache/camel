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
package org.apache.camel.main.download;

import org.apache.camel.impl.engine.SimpleCamelContext;
import org.apache.camel.tooling.maven.MavenGav;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class KnownDependenciesResolverTest {

    @Test
    void mavenGavForClass_returnsClassScopedDependency() {
        KnownDependenciesResolver resolver = new KnownDependenciesResolver(new SimpleCamelContext(), null, null);
        resolver.loadKnownDependencies();

        MavenGav dependency = resolver.mavenGavForClass(SomeClass.class.getName());

        assertNotNull(dependency);
        assertEquals(dependency.getGroupId(), "com.example");
        assertEquals(dependency.getArtifactId(), "class-scoped");
        assertEquals(dependency.getVersion(), "1.0.0");
    }

    @Test
    void mavenGavForClass_returnsPackageScopedDependency() {
        KnownDependenciesResolver resolver = new KnownDependenciesResolver(new SimpleCamelContext(), null, null);
        resolver.loadKnownDependencies();

        MavenGav dependency = resolver.mavenGavForClass(SomeClass.class.getPackage().getName());

        assertNotNull(dependency);
        assertEquals(dependency.getGroupId(), "org.example");
        assertEquals(dependency.getArtifactId(), "package-scoped");
        assertEquals(dependency.getVersion(), "2.0.0");
    }

    public static class SomeClass {
    }

    @Test
    void anImportResolvesAnyClassOfAComponent() {
        // CAMEL-25239: any class of a component a source imports, not only the component class itself, such as the
        // constants of the headers of a component that a kamelet uses
        KnownDependenciesResolver resolver = new KnownDependenciesResolver(new SimpleCamelContext(), null, null);
        resolver.loadKnownDependencies();

        assertImport(resolver, "org.apache.camel.component.aws2.s3.AWS2S3Component", "camel-aws2-s3");
        assertImport(resolver, "org.apache.camel.component.aws2.s3.AWS2S3Constants", "camel-aws2-s3");
        assertImport(resolver, "org.apache.camel.component.aws2.s3.utils.AWS2S3Utils", "camel-aws2-s3");
        // a sibling package of another component is not mistaken for it
        assertImport(resolver, "org.apache.camel.component.aws2.s3vectors.AWS2S3VectorsConstants", "camel-aws2-s3-vectors");
        // a sub package of another component's package wins for its own classes
        assertImport(resolver, "org.apache.camel.component.file.remote.SftpConstants", "camel-ftp");
        assertImport(resolver, "org.apache.camel.component.file.GenericFile", "camel-file");
        // the libraries still resolve as before
        assertGav(resolver, "com.fasterxml.jackson.annotation.JsonProperty", "com.fasterxml.jackson.core", "jackson-annotations");
        // a component in a base package does not claim every class in it
        assertNull(resolver.mavenGavForImport("org.apache.camel.Exchange"));
        assertNull(resolver.mavenGavForImport("org.apache.camel.component.Anything"));
        // a class looked up at runtime (often only probed for) still needs the component class itself
        assertNull(resolver.mavenGavForClass("org.apache.camel.component.aws2.s3.AWS2S3Constants"));
        assertGav(resolver, "org.apache.camel.component.aws2.s3.AWS2S3Component", "org.apache.camel", "camel-aws2-s3");
    }

    private static void assertImport(KnownDependenciesResolver resolver, String className, String artifactId) {
        MavenGav gav = resolver.mavenGavForImport(className);
        assertNotNull(gav, className);
        assertEquals("org.apache.camel", gav.getGroupId(), className);
        assertEquals(artifactId, gav.getArtifactId(), className);
        assertNotNull(gav.getVersion(), className + " version is null");
    }

    private static void assertGav(KnownDependenciesResolver resolver, String className, String groupId, String artifactId) {
        MavenGav gav = resolver.mavenGavForClass(className);
        assertNotNull(gav, className);
        assertEquals(groupId, gav.getGroupId(), className);
        assertEquals(artifactId, gav.getArtifactId(), className);
        String version = gav.getVersion();
        assertNotNull(version, className + " version is null");
        assertFalse(version.startsWith("${"), className + " version is an unresolved placeholder: " + version);
    }
}
