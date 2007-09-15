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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Runs Camel embedded with META-INF/services/*.xml spring files to try create DOT files for the
 * routing rules, then converts the DOT files into another format such as PNG
 *
 * @version $Revision: 1.1 $
 * @goal dot
 * @requiresDependencyResolution runtime
 * @phase prepare-package
 * @execute phase="test-compile"
 * @see <a href="http://www.graphviz.org/">GraphViz</a>
 */
public class DotMojo extends AbstractMavenReport {
    public static final String[] DEFAULT_GRAPHVIZ_OUTPUT_TYPES = {"png", "svg", "cmapx"};
    /**
     * Subdirectory for report.
     */
    protected static final String SUBDIRECTORY = "cameldoc";
    private String indexHtmlContent;
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
     * @parameter default-value="${project.build.directory}/site/cameldoc"
     * @readonly
     * @required
     */
    private File outputDirectory;
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
    //
    // For running Camel embedded
    //-------------------------------------------------------------------------
    //
    /**
     * The duration to run the application for which by default is in milliseconds.
     *
     * @parameter expression="2s"
     * @readonly
     */
    protected String duration;
    /**
     * Whether we should boot up camel with the META-INF/services/*.xml to generate the DOT file
     *
     * @parameter expression="true"
     * @readonly
     */
    protected boolean runCamel;

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
        return SUBDIRECTORY + "/index";
    }

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute() throws MojoExecutionException {
        this.execute(this.buildDirectory, Locale.getDefault());
        try {
            writeIndexHtmlFile("index.html", indexHtmlContent);
        }
        catch (IOException e) {
            throw new MojoExecutionException("Failed: " + e, e);
        }
    }

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#executeReport(Locale)
     */
    protected void executeReport(final Locale locale) throws MavenReportException {
        try {
            this.execute(this.outputDirectory, locale);

            Sink kitchenSink = getSink();
            if (kitchenSink != null) {
                kitchenSink.rawText(indexHtmlContent);
            }
            else {
                writeIndexHtmlFile("index.html", indexHtmlContent);
            }
        }
        catch (Exception e) {
            final MavenReportException ex = new MavenReportException(e.getMessage());
            ex.initCause(e.getCause());
            throw ex;
        }
    }

    /**
     * Executes DOT generator.
     *
     * @param outputDir report output directory.
     * @param locale    report locale.
     * @throws MojoExecutionException if there were any execution errors.
     */
    protected void execute(final File outputDir, final Locale locale) throws MojoExecutionException {
        try {
            runCamelEmbedded(outputDir);
        }
        catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Failed: " + e, e);
        }
        outputDir.mkdirs();

        List<File> files = new ArrayList<File>();
        appendFiles(files, outputDirectory);

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

                StringWriter buffer = new StringWriter();
                PrintWriter out = new PrintWriter(buffer);
                printHtmlHeader(out);
                printHtmlFileHeader(out, file);
                for (int j = 0; j < graphvizOutputTypes.length; j++) {
                    String format = graphvizOutputTypes[j];
                    String generated = convertFile(file, format);

                    if (format.equals("cmapx")) {
                        // lets include the generated file inside the html
                        addFileToBuffer(out, new File(generated));
                    }
                }
                printHtmlFileFooter(out, file);
                printHtmlFooter(out);

                String content = buffer.toString();
                String name = file.getName();
                if (name.equalsIgnoreCase("routes.dot") || i == 0) {
                    indexHtmlContent = content;
                }
                int idx = name.lastIndexOf(".");
                if (idx >= 0) {
                    name = name.substring(0, idx);
                    name += ".html";
                }
                writeIndexHtmlFile(name, content);
            }
        }
        catch (CommandLineException e) {
            throw new MojoExecutionException("Failed: " + e, e);
        }
        catch (IOException e) {
            throw new MojoExecutionException("Failed: " + e, e);
        }
    }

    protected void runCamelEmbedded(File outputDir) throws DependencyResolutionRequiredException {
        if (runCamel) {
            getLog().info("Running Camel embedded to load META-INF/spring/*.xml files");

            List list = project.getTestClasspathElements();
            getLog().debug("Using classpath: " + list);

            EmbeddedMojo mojo = new EmbeddedMojo();
            mojo.setClasspathElements(list);
            mojo.setDotEnabled(true);
            mojo.setOutputDirectory(outputDirectory.getAbsolutePath());
            mojo.setDuration(duration);
            mojo.setLog(getLog());
            mojo.setPluginContext(getPluginContext());
            try {
                mojo.executeWithoutWrapping();
            }
            catch (Exception e) {
                getLog().error("Failed to run Camel embedded: " + e, e);
            }
        }
    }

    protected void writeIndexHtmlFile(String fileName, String content) throws IOException {
        //File dir = new File(outputDirectory, SUBDIRECTORY);
        File dir = outputDirectory;
        dir.mkdirs();
        File html = new File(dir, fileName);
        PrintWriter out = null;
        try {
            out = new PrintWriter(new FileWriter(html));
            out.println("<html>");
            out.println("<head>");
            out.println("</head>");
            out.println("<body>");
            out.println();
            if (content == null) {
                out.write("<p>No EIP diagrams available</p>");
            }
            else {
                out.write(content);
            }
            out.println("</body>");
            out.println("</html>");
        }
        finally {
            String description = "Failed to close html output file";
            close(out, description);
        }
    }

    protected void printHtmlHeader(PrintWriter out) {
        out.println("<h1>Camel EIP Patterns</h1>");
        out.println();
    }

    protected void printHtmlFileHeader(PrintWriter out, File file) {
        out.println("<p>");
        out.println("  <img src='" + removeFileExtension(file.getName()) + ".png' usemap='#CamelRoutes'>");
    }

    protected void printHtmlFileFooter(PrintWriter out, File file) {
        out.println("  </img>");
        out.println("</p>");
        out.println();
    }

    protected void printHtmlFooter(PrintWriter out) {
        out.println();
    }

    protected void close(Closeable closeable, String description) {
        if (closeable != null) {
            try {
                closeable.close();
            }
            catch (IOException e) {
                getLog().warn(description + ": " + e);
            }
        }
    }

    protected String convertFile(File file, String format) throws CommandLineException {
        String generatedFileName = removeFileExtension(file.getAbsolutePath()) + "." + format;
        Log log = getLog();

        Commandline cl = new Commandline();
        cl.setExecutable(executable);
        cl.createArgument().setValue("-T" + format);
        cl.createArgument().setValue("-o");
        cl.createArgument().setValue(generatedFileName);
        cl.createArgument().setValue(file.getAbsolutePath());

        log.debug("executing: " + cl.toString());

        CommandLineUtils.StringStreamConsumer stdout = new CommandLineUtils.StringStreamConsumer();
        CommandLineUtils.StringStreamConsumer stderr = new CommandLineUtils.StringStreamConsumer();

        int exitCode = CommandLineUtils.executeCommandLine(cl, stdout, stderr);

        String output = stdout.getOutput();
        if (output.length() > 0) {
            log.debug(output);
        }
        String errOutput = stderr.getOutput();
        if (errOutput.length() > 0) {
            log.warn(errOutput);
        }
        return generatedFileName;
    }

    protected String removeFileExtension(String name) {
        int idx = name.lastIndexOf(".");
        if (idx > 0) {
            return name.substring(0, idx);
        }
        else {
            return name;
        }
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

    private void addFileToBuffer(PrintWriter out, File file) throws MojoExecutionException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                else {
                    out.println(line);
                }
            }
        }
        catch (IOException e) {
            throw new MojoExecutionException("Failed: " + e, e);
        }
        finally {
            close(reader, "cmapx file");
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
                "camel-maven-plugin",
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