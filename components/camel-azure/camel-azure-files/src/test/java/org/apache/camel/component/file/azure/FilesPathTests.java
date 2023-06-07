package org.apache.camel.component.file.azure;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class FilesPathTests extends CamelTestSupport {

    @Test
    void splitWithoutSeparatorShouldReturnInput() {
        // by observation, Camel devs were uncertain what is returned ...
        assertArrayEquals(new String[] { "a path" }, FilesPath.split("a path"));
    }
}
