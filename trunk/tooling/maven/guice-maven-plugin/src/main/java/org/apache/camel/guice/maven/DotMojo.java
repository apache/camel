/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.guice.maven;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import org.apache.camel.util.IOHelper;

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
 * Runs a Camel using the
 * <code>jndi.properties</code> file on the classpath to
 * way to <a href="http://camel.apache.org/guice.html">bootstrap via Guice</a>
 * then the DOT files are created, then they are converted from DOT files to another format such as PNG
 *
 * @version 
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
    //
    // For running Camel embedded
    // -------------------------------------------------------------------------
    //
    /**
     * The duration to run the application for which by default is in
     * milliseconds. A value <= 0 will run forever.
     * Adding a s indicates seconds - eg "5s" means 5 seconds.
     *
     * @parameter property="2s"
     */
    protected String duration;

    /**
     * Whether we should boot up camel with the jndi.properties file to
     * generate the DOT file
     *
     * @parameter property="true"
     */
    protected boolean runCamel;

    /**
     * Should we try run the DOT executable on the generated .DOT file to
     * generate images
     *
     * @parameter property="true"
     */
    protected boolean useDot;

    /**
     * The main class to execute.
     *
     * @parameter property="camel.mainClass"
     *            default-value="org.apache.camel.guice.Main"
     * @required
     */
    private String mainClass;

    /**
     * Reference to Maven 2 Project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * Base output directory.
     *
     * @parameter property="project.build.directory"
     * @required
     */
    private File buildDirectory;

    /**
     * Base output directory for reports.
     *
     * @parameter default-value="${project.build.directory}/site/cameldoc"
     * @required
     */
    private File outputDirectory;


    /**
     * In the case of multiple camel contexts, setting aggregate == true will
     * aggregate all into a monolithic context, otherwise they will be processed
     * independently.
     *
     * @parameter
     */
    private String aggregate;

    /**
     * GraphViz executable location; visualization (images) will be generated
     * only if you install this program and set this property to the executable
     * dot (dot.exe on Win).
     *
     * @parameter property="dot"
     */
    private String executable;

    /**
     * Graphviz output types. Default is png. Possible values: png, jpg, gif,
     * svg.
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

    private String indexHtmlContent;

    /**
     * @param locale report locale.
     * @return report description.
     * @see org.apache.maven.reporting.MavenReport#getDescription(java.util.Locale)
     */
    public String getDescription(final Locale locale) {
        return getBundle(locale).getString("report.dot.description");
    }

    /**
     * @see org.apache.maven.reporting.MavenReport#getName(java.util.Locale)
     */
    public String getName(final Locale locale) {
        return getBundle(locale).getString("report.dot.name");
    }

    public String getOutputName() {
        return SUBDIRECTORY + "/index";
    }

    public String getAggregate() {
        return aggregate;
    }

    public void setAggregate(String aggregate) {
        this.aggregate = aggregate;
    }

    public boolean isUseDot() {
        return useDot;
    }

    public void setUseDot(boolean useDot) {
        this.useDot = useDot;
    }

    public void execute() throws MojoExecutionException {
        this.execute(this.buildDirectory, Locale.getDefault());
        try {
            writeIndexHtmlFile(outputDirectory, "index.html", indexHtmlContent);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed: " + e, e);
        }
    }

    protected void executeReport(final Locale locale) throws MavenReportException {
        try {
            this.execute(this.outputDirectory, locale);

            Sink kitchenSink = getSink();
            if (kitchenSink != null) {
                kitchenSink.rawText(indexHtmlContent);
            } else {
                writeIndexHtmlFile(outputDirectory, "index.html", indexHtmlContent);
            }
        } catch (Exception e) {
            final MavenReportException ex = new MavenReportException(e.getMessage());
            ex.initCause(e.getCause());
            throw ex;
        }
    }

    /**
     * Executes DOT generator.
     *
     * @param outputDir report output directory.
     * @param locale report locale.
     * @throws org.apache.maven.plugin.MojoExecutionException if there were any execution errors.
     */
    protected void execute(final File outputDir, final Locale locale) throws MojoExecutionException {

        try {
            runCamelEmbedded(outputDir);
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Failed: " + e, e);
        }
        outputDir.mkdirs();

        List<File> files = new ArrayList<File>();
        appendFiles(files, outputDirectory);

        if (graphvizOutputTypes == null) {
            if (graphvizOutputType == null) {
                graphvizOutputTypes = DEFAULT_GRAPHVIZ_OUTPUT_TYPES;
            } else {
                graphvizOutputTypes = new String[] {graphvizOutputType};
            }
        }
        try {
            Set<String> contextNames = new HashSet<String>();
            for (File file : files) {
                String contextName = file.getParentFile().getName();
                contextNames.add(contextName);
            }

            boolean multipleCamelContexts = contextNames.size() > 1;
            int size = files.size();
            for (int i = 0; i < size; i++) {
                File file = files.get(i);
                String contextName = null;
                if (multipleCamelContexts) {
                    contextName = file.getParentFile().getName();
                }

                getLog().info("Generating contextName: " + contextName + " file: " + file + "");

                generate(i, file, contextName);
            }

            if (multipleCamelContexts) {
                // lets generate an index page which lists each indiviual
                // CamelContext file
                StringWriter buffer = new StringWriter();
                PrintWriter out = new PrintWriter(buffer);

                out.println("<h1>Camel Contexts</h1>");
                out.println();

                out.println("<ul>");
                for (String contextName : contextNames) {
                    out.print("  <li><a href='");
                    out.print(contextName);
                    out.print("/routes.html'>");
                    out.print(contextName);
                    out.println("</a></li>");
                }
                out.println("</ul>");
                indexHtmlContent = buffer.toString();
            }
        } catch (CommandLineException e) {
            throw new MojoExecutionException("Failed: " + e, e);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed: " + e, e);
        }
    }

    private void generate(int index, File file, String contextName) throws CommandLineException,
        MojoExecutionException, IOException {

        StringWriter buffer = new StringWriter();
        PrintWriter out = new PrintWriter(buffer);
        printHtmlHeader(out, contextName);
        printHtmlFileHeader(out, file);
        for (String format : graphvizOutputTypes) {
            String generated = convertFile(file, format);

            if (format.equals("cmapx") && generated != null) {
                // lets include the generated file inside the html
                addFileToBuffer(out, new File(generated));
            }
        }
        printHtmlFileFooter(out, file);
        printHtmlFooter(out);

        String content = buffer.toString();
        String name = file.getName();
        if (name.equalsIgnoreCase("routes.dot") || index == 0) {
            indexHtmlContent = content;
        }
        int idx = name.lastIndexOf(".");
        if (idx >= 0) {
            name = name.substring(0, idx);
            name += ".html";
        }
        writeIndexHtmlFile(file.getParentFile(), name, content);
    }

    protected void runCamelEmbedded(File outputDir) throws DependencyResolutionRequiredException {
        if (runCamel) {
            getLog().info("Running Camel embedded to load jndi.properties file from the classpath");

            List<?> list = project.getTestClasspathElements();
            getLog().debug("Using classpath: " + list);

            EmbeddedMojo mojo = new EmbeddedMojo();
            mojo.setClasspathElements(list);
            mojo.setDotEnabled(true);
            mojo.setMainClass(mainClass);
            if ("true".equals(getAggregate())) {
                mojo.setDotAggregationEnabled(true);
            }
            mojo.setOutputDirectory(outputDirectory.getAbsolutePath());
            mojo.setDuration(duration);
            mojo.setLog(getLog());
            mojo.setPluginContext(getPluginContext());
            try {
                mojo.executeWithoutWrapping();
            } catch (Exception e) {
                getLog().error("Failed to run Camel embedded: " + e, e);
            }
        }
    }

    protected void writeIndexHtmlFile(File dir, String fileName, String content) throws IOException {
        // File dir = outputDirectory;
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
            } else {
                out.write(content);
            }
            out.println("</body>");
            out.println("</html>");
        } finally {
            String description = "Failed to close html output file";
            close(out, description);
        }
    }

    protected void printHtmlHeader(PrintWriter out, String contextName) {
        if (contextName != null) {
            out.println("<h1>EIP Patterns for CamelContext: " + contextName + "</h1>");
        } else {
            out.println("<h1>Camel EIP Patterns</h1>");
        }
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
            } catch (IOException e) {
                getLog().warn(description + ": " + e);
            }
        }
    }

    protected String convertFile(File file, String format) throws CommandLineException {
        Log log = getLog();
        if (!useDot) {
            log.info("DOT generation disabled");
            return null;
        }
        if (this.executable == null || this.executable.length() == 0) {
            log.warn("Parameter <executable/> was not set in the pom.xml.  Skipping conversion.");
            return null;
        }

        String generatedFileName = removeFileExtension(file.getAbsolutePath()) + "." + format;
        Commandline cl = new Commandline();
        cl.setExecutable(executable);
        cl.createArg().setValue("-T" + format);
        cl.createArg().setValue("-o");
        cl.createArg().setValue(generatedFileName);
        cl.createArg().setValue(file.getAbsolutePath());

        log.debug("executing: " + cl.toString());

        CommandLineUtils.StringStreamConsumer stdout = new CommandLineUtils.StringStreamConsumer();
        CommandLineUtils.StringStreamConsumer stderr = new CommandLineUtils.StringStreamConsumer();

        CommandLineUtils.executeCommandLine(cl, stdout, stderr);

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
        } else {
            return name;
        }
    }

    private void appendFiles(List<File> output, File file) {
        if (file.isDirectory()) {
            appendDirectory(output, file);
        } else {
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
            reader = IOHelper.buffered(new FileReader(file));
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                } else {
                    out.println(line);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed: " + e, e);
        } finally {
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
        return ResourceBundle.getBundle("camel-maven-plugin", locale, this.getClass().getClassLoader());
    }

    protected Renderer getSiteRenderer() {
        return this.renderer;
    }

    protected String getOutputDirectory() {
        return this.outputDirectory.getAbsolutePath();
    }

    protected MavenProject getProject() {
        return this.project;
    }
}
