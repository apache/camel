package org.apache.camel.main.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.camel.impl.engine.SimpleCamelContext;
import org.apache.camel.tooling.maven.MavenGav;
import org.junit.jupiter.api.Test;

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
}