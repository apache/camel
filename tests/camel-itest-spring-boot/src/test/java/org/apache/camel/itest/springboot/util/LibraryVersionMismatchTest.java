package org.apache.camel.itest.springboot.util;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.itest.springboot.util.ArquillianPackager;
import org.apache.camel.itest.springboot.util.DependencyResolver;
import org.apache.commons.io.IOUtils;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolverSystem;
import org.junit.Assert;
import org.junit.Test;

/**
 * Prints information about the libraries required by the module and what it gets at runtime in a spring-boot deployment.
 */
public class LibraryVersionMismatchTest {

    @Test
    public void testAllComponents() throws Exception {

        List<String> components = Arrays.asList(new File("../../components-starter")
                .list((d, f) -> f.startsWith("camel-") && (new File(d, f).isDirectory())))
                .stream()
                .map(name -> name.replace("-starter", ""))
                .sorted().collect(Collectors.toList());

        boolean fail = false;
        for(String moduleName : components) {
            System.out.println("------------- " + moduleName + " -------------");
            boolean compFail = testLibrary(moduleName);
            fail = fail || compFail;
        }

        //Assert.assertFalse("Dependencies changed between original and user version", fail);
    }


    public boolean testLibrary(String moduleName) throws Exception {

        MavenResolverSystem resolver = Maven.resolver();

        MavenResolvedArtifact[] original = resolver.resolve("org.apache.camel:" + moduleName + ":2.18-SNAPSHOT").withTransitivity().asResolvedArtifact();
        Map<String, String> originalMap = versionMap(original);

        File userPom = createUserPom(moduleName);

        MavenResolvedArtifact[] user = resolver.loadPomFromFile(userPom).importRuntimeDependencies().resolve().withTransitivity().asResolvedArtifact();
        Map<String, String> userMap = versionMap(user);

        boolean changed = false;
        for (String dep : originalMap.keySet()) {
            String originalVersion = originalMap.get(dep);
            String userVersion = userMap.get(dep);

            if (userVersion == null) {
                System.out.println("WARNING - " + moduleName + " - Library not present in user version: " + dep);
            } else if (!originalVersion.equals(userVersion)) {
                System.out.println("ERROR - " + moduleName + " - Version mismatch for " + dep + ": original=" + originalVersion + ", user=" + userVersion);
                changed = true;
            }
        }

        return !changed;
    }

    private Map<String, String> versionMap(MavenResolvedArtifact[] artifacts) {
        Map<String, String> versions = new TreeMap<>();
        for (MavenResolvedArtifact art : artifacts) {
            versions.put(art.getCoordinate().getGroupId() + ":" + art.getCoordinate().getArtifactId(), art.getCoordinate().getVersion());
        }
        return versions;
    }

    private static File createUserPom(String moduleName) throws Exception {

        String pom;
        try (InputStream pomTemplate = ArquillianPackager.class.getResourceAsStream("/application-pom.xml")) {
            pom = IOUtils.toString(pomTemplate);
        }

        Map<String, String> resolvedProperties = new TreeMap<>();
        Pattern propPattern = Pattern.compile("(\\$\\{[^}]*\\})");
        Matcher m = propPattern.matcher(pom);
        while (m.find()) {
            String property = m.group();
            String resolved = DependencyResolver.resolveParentProperty(property);
            resolvedProperties.put(property, resolved);
        }

        for (String property : resolvedProperties.keySet()) {
            pom = pom.replace(property, resolvedProperties.get(property));
        }

        pom = pom.replace("#{module}", moduleName);

        File pomFile = new File("target/library-version-mismatch-spring-boot-pom.xml");
        try (FileWriter fw = new FileWriter(pomFile)) {
            IOUtils.write(pom, fw);
        }

        return pomFile;
    }
}
