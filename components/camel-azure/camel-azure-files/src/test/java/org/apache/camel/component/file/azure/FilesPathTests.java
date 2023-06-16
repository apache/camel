package org.apache.camel.component.file.azure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@SuppressWarnings("static-method")
public class FilesPathTests {

    @Test
    void splitAbsolutePreservingRootShouldReturnRootAndSteps() {
        assertArrayEquals(new String[] { "/", "1", "2" }, FilesPath.splitToSteps("/1/2", true));
    }

    @Test
    void splitAbsoluteWithoutPreservingRootShouldReturnStepsOnly() {
        assertArrayEquals(new String[] { "1", "2" }, FilesPath.splitToSteps("/1/2", false));
    }

    @Test
    void splitRelativePreservingRootShouldReturnStepsOnly() {
        assertArrayEquals(new String[] { "1", "2" }, FilesPath.splitToSteps("1/2", true));
    }

    @Test
    void splitRootPreservingRootShouldReturnRoot() {
        assertArrayEquals(new String[] { "/" }, FilesPath.splitToSteps("/", true));
    }

    @Test
    void splitWithoutSeparatorShouldReturnInput() {
        // by observation, Camel devs were uncertain what is returned ...
        assertArrayEquals(new String[] { "a path" }, FilesPath.split("a path"));
    }

}
