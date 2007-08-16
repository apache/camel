/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.maven;

import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Converts the DOT files into another format such as PNG
 *
 * @version $Revision: 1.1 $
 * @goal dot
 * @phase prepare-package
 * @see <a href="http://www.graphviz.org/">GraphViz</a>
 */
public class DotMojo extends AbstractMavenReport {
    public static final String[] DEFAULT_GRAPHVIZ_OUTPUT_TYPES = { "png", "svg", "cmapx" };

    /**
     * Subdirectory for report.
     */
    protected static final String SUBDIRECTORY = "cameldoc";
    /**
     * Reference to Maven 2 Project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;
    /**
     * Base output directory.
     *
     * @parameter expression="${project.build.directory}"
     * @required
     * @readonly
     */
    private File buildDirectory;
    /**
     * Base output directory for reports.
     *
     * @parameter expression="${project.reporting.outputDirectory}"
     * @readonly
     * @required
     */
    private File outputDirectory;
    /**
     * The input directory used to find the dot files
     *
     * @parameter default-value="${project.build.directory}/site/cameldoc"
     * @required
     */
    private File resources;
    /**
     * GraphViz executable location; visualization (images) will be
     * generated only if you install this program and set this property to the
     * executable dot (dot.exe on Win).
     *
     * @parameter expression="dot"
     */
    private String executable;
    /**
     * Graphviz output types. Default is png. Possible values: png, jpg, gif, svg.
     *
     * @required
     */
    private String graphvizOutputType;
    /**
     * Graphviz output types. Possible values: png, jpg, gif, svg.
     *
     * @parameter
     */
    private String[] graphvizOutputTypes;
    /**
     * Doxia SiteRender.
     *
     * @component
     */
    private Renderer renderer;

    /**
     * @param locale report locale.
     * @return report description.
     * @see org.apache.maven.reporting.MavenReport#getDescription(Locale)
     */
    public String getDescription(final Locale locale) {
        return getBundle(locale).getString("report.description");
    }

    /**
     * @see org.apache.maven.reporting.MavenReport#getName(Locale)
     */
    public String getName(final Locale locale) {
        return getBundle(locale).getString("report.name");
    }

    /**
     * @see org.apache.maven.reporting.MavenReport#getOutputName()
     */
    public String getOutputName() {
        return SUBDIRECTORY + "/index.html";
    }

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute() throws MojoExecutionException {
        this.execute(this.buildDirectory, Locale.getDefault());
    }

    /**
     * Executes DOT generator.
     *
     * @param outputDir report output directory.
     * @param locale    report locale.
     * @throws MojoExecutionException if there were any execution errors.
     */
    private void execute(final File outputDir, final Locale locale) throws MojoExecutionException {
        outputDir.mkdirs();

        List<File> files = new ArrayList<File>();
        appendFiles(files, resources);

        if (graphvizOutputTypes == null) {
            if (graphvizOutputType == null) {
                graphvizOutputTypes = DEFAULT_GRAPHVIZ_OUTPUT_TYPES;
            }
            else {
            graphvizOutputTypes = new String[]{graphvizOutputType};
            }
        }
        try {
            for (int i = 0; i < files.size(); i++) {
                File file = (File) ((List) files).get(i);
                for (int j = 0; j < graphvizOutputTypes.length; j++) {
                    String format = graphvizOutputTypes[j];
                    convertFile(file, format, getLog());
                }
            }
        }
        catch (CommandLineException e) {
            throw new MojoExecutionException("Failed: " + e, e);
        }
    }

    protected int convertFile(File file, String format, Log log1) throws CommandLineException {
        Commandline cl = new Commandline();
        cl.setExecutable(executable);
        cl.createArgument().setValue("-T" + format);
        cl.createArgument().setValue("-o");
        cl.createArgument().setValue(file.getAbsolutePath().replace(".dot", "." + format));
        cl.createArgument().setValue(file.getAbsolutePath());

        log1.debug("executing: " + cl.toString());

        CommandLineUtils.StringStreamConsumer stdout = new CommandLineUtils.StringStreamConsumer();
        CommandLineUtils.StringStreamConsumer stderr = new CommandLineUtils.StringStreamConsumer();

        int exitCode = CommandLineUtils.executeCommandLine(cl, stdout, stderr);

        String output = stdout.getOutput();
        if (output.length() > 0) {
            log1.debug(output);
        }
        String errOutput = stderr.getOutput();
        if (errOutput.length() > 0) {
            log1.warn(errOutput);
        }
        return exitCode;
    }

    private void appendFiles(List<File> output, File file) {
        if (file.isDirectory()) {
            appendDirectory(output, file);
        }
        else {
            if (isValid(file)) {
                output.add(file);
            }
        }
    }

    private void appendDirectory(List<File> output, File dir) {
        File[] files = dir.listFiles();
        for (File file : files) {
            appendFiles(output, file);
        }
    }

    private boolean isValid(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".dot");
    }

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#executeReport(Locale)
     */
    protected void executeReport(final Locale locale) throws MavenReportException {
        try {
            this.execute(this.outputDirectory, locale);
        }
        catch (MojoExecutionException e) {
            final MavenReportException ex = new MavenReportException(e.getMessage());
            ex.initCause(e.getCause());
            throw ex;
        }
    }

    /**
     * Gets resource bundle for given locale.
     *
     * @param locale locale
     * @return resource bundle
     */
    protected ResourceBundle getBundle(final Locale locale) {
        return ResourceBundle.getBundle(
                "camel-dot-maven-plugin",
                locale,
                this.getClass().getClassLoader());
    }

    protected Renderer getSiteRenderer() {
        return this.renderer;
    }

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#getOutputDirectory()
     */
    protected String getOutputDirectory() {
        return this.outputDirectory.getAbsolutePath();
    }

    protected MavenProject getProject() {
        return this.project;
    }
}