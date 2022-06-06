package org.apache.camel.dsl.jbang.core.commands;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import picocli.CommandLine;

abstract class BaseExport extends CamelCommand {

    protected static final String BUILD_DIR = ".camel-jbang/work";

    protected static final String[] SETTINGS_PROP_SOURCE_KEYS = new String[] {
            "camel.main.routesIncludePattern",
            "camel.component.properties.location",
            "camel.component.kamelet.location",
            "camel.jbang.classpathFiles"
    };

    @CommandLine.Option(names = { "--gav" }, description = "The Maven group:artifact:version", required = true)
    protected String gav;

    @CommandLine.Option(names = { "--java-version" }, description = "Java version (11 or 17)",
                        defaultValue = "11")
    protected String javaVersion;

    @CommandLine.Option(names = { "--kamelets-version" }, description = "Apache Camel Kamelets version",
                        defaultValue = "0.8.1")
    protected String kameletsVersion;

    @CommandLine.Option(names = { "-dir", "--directory" }, description = "Directory where the project will be exported",
                        defaultValue = ".")
    protected String exportDir;

    @CommandLine.Option(names = { "--fresh" }, description = "Make sure we use fresh (i.e. non-cached) resources")
    protected boolean fresh;

    public BaseExport(CamelJBangMain main) {
        super(main);
    }

    protected static void safeCopy(File source, File target, boolean override) throws Exception {
        if (!source.exists()) {
            return;
        }

        if (!target.exists()) {
            Files.copy(source.toPath(), target.toPath());
        } else if (override) {
            Files.copy(source.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    protected static String getScheme(String name) {
        int pos = name.indexOf(":");
        if (pos != -1) {
            return name.substring(0, pos);
        }
        return null;
    }

    protected Integer runSilently() throws Exception {
        Run run = new Run(getMain());
        Integer code = run.runSilent();
        return code;
    }

    protected void safeCopy(InputStream source, File target) throws Exception {
        if (source == null) {
            return;
        }

        File dir = target.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        if (!target.exists()) {
            Files.copy(source, target.toPath());
        }
    }
}
