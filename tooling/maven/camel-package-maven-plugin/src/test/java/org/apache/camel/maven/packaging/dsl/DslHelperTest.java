package org.apache.camel.maven.packaging.dsl;

import java.io.File;
import java.util.List;

import org.apache.camel.maven.packaging.dsl.component.ComponentDslGenerator;
import org.apache.camel.tooling.util.Strings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DslHelperTest {

    @Test
    public void testLoadAllJavaFiles() {
        final List<File> files = DslHelper.loadAllJavaFiles(new File("/Users/oalsafi/Work/Apache/camel/core/camel-componentdsl/src/main/java"), "org.apache.camel.builder.component.dsl");
        files.forEach(file -> {
            System.out.println(Strings.before(file.getName(), "."));
        });
    }
}