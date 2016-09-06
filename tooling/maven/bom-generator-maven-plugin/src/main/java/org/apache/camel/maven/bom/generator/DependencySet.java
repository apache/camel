package org.apache.camel.maven.bom.generator;

import java.util.HashSet;
import java.util.Set;

/**
 * Inclusion and exclusion rules for artifacts.
 */
public class DependencySet {

    private Set<String> includes = new HashSet<>();

    private Set<String> excludes = new HashSet<>();

    public DependencySet() {
    }

    public Set<String> getIncludes() {
        return includes;
    }

    public void setIncludes(Set<String> includes) {
        this.includes = includes;
    }

    public Set<String> getExcludes() {
        return excludes;
    }

    public void setExcludes(Set<String> excludes) {
        this.excludes = excludes;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DependencySet{");
        sb.append("includes=").append(includes);
        sb.append(", excludes=").append(excludes);
        sb.append('}');
        return sb.toString();
    }
}
