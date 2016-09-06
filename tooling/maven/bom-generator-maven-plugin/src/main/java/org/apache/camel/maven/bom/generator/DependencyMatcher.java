package org.apache.camel.maven.bom.generator;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.model.Dependency;
import org.codehaus.plexus.util.SelectorUtils;

/**
 * A matcher for Maven dependencies based on a collection of rules.
 */
public class DependencyMatcher {

    private static final String ARTIFACT_FORMAT = "%s:%s:%s:%s:%s";
    private static final Pattern ARTIFACT_PATTERN = Pattern.compile("(?<groupId>[^:]+):(?<artifactId>[^:]+)(:(?<version>[^:]+))?(:(?<type>[^:]+))?(:(?<classifier>[^:]+))?");

    private Collection<String> selectors;

    public DependencyMatcher(Collection<String> selectors) {
        this.selectors = selectors;
    }

    public boolean matches(Dependency artifact) {

        Set<String> expanded = expand(selectors);
        String coordinates = toCoordinates(artifact);

        for (String e : expanded) {
            if (SelectorUtils.match(e, coordinates)) {
                return true;
            }
        }
        return false;
    }

    private String toCoordinates(Dependency artifact) {
        return String.format(ARTIFACT_FORMAT, artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getVersion(),
                artifact.getType(),
                artifact.getClassifier());
    }

    private Set<String> expand(Collection<String> set) {
        Set<String> result = new HashSet<>();
        if (set != null) {
            for (String exclusion : set) {
                Matcher m = ARTIFACT_PATTERN.matcher(exclusion);
                if (!m.matches()) {
                    throw new IllegalArgumentException("Pattern: " + exclusion + " doesn't have the required format.");
                }
                String groupId = m.group("groupId");
                String artifactId = m.group("artifactId");
                String version = m.group("version");
                String type = m.group("type");
                String classifier = m.group("classifier");

                version = version != null ? version : "*";
                type = type != null ? type : "*";
                classifier = classifier != null ? classifier : "*";

                result.add(String.format(ARTIFACT_FORMAT, groupId, artifactId, version, type, classifier));
            }
        }
        return result;
    }

}
