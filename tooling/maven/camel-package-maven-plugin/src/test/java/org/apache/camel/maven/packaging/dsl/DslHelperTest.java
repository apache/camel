package org.apache.camel.maven.packaging.dsl;

import java.io.File;
import java.util.List;

import org.apache.camel.maven.packaging.dsl.component.ComponentDslGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DslHelperTest {

    @Test
    public void testLoadAllJavaFiles() {
        final List<File> files = DslHelper.loadAllJavaFiles(new File("/Users/oalsafi/Work/Apache/camel/core/camel-componentdsl/src/main/java"), ComponentDslGenerator.COMPONENT_DSL_PACKAGE_NAME + ".dsl");
        System.out.println(files);
    }
}