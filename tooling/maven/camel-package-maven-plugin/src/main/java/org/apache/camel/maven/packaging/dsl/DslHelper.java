package org.apache.camel.maven.packaging.dsl;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class DslHelper {
    private DslHelper(){}

    public static List<File> loadAllJavaFiles(final File dir, final String targetJavaPackageName) {
        final File allComponentsDslEndpointFactory = new File(dir, targetJavaPackageName.replace('.', '/'));
        final File[] files = allComponentsDslEndpointFactory.listFiles();

        if (files == null) {
            return Collections.emptyList();
        }

        // load components
        return Arrays.stream(files)
                .filter(file -> file.isFile() && file.getName().endsWith(".java") && file.exists())
                .sorted()
                .collect(Collectors.toList());
    }
}
