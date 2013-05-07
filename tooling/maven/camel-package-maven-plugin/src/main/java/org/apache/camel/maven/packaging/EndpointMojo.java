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
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.util.IOUtil;

/**
 * Generates a HTML report for all the Camel endpoints and their configuration parameters
 *
 * @goal endpoints
 * @phase site
 */
public class EndpointMojo extends AbstractMavenReport {

    /**
     * Reference to Maven Project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * @component
     * @required
     * @readonly
     */
    private Renderer renderer;

    /**
     * Base output directory for reports.
     *
     * @parameter default-value="${project.build.directory}/site"
     * @required
     */
    private File outputDirectory;


    protected void executeReport(final Locale locale) throws MavenReportException {
        try {
            File dir = new File(project.getBuild().getOutputDirectory(), "org/apache/camel/component");
            getLog().info("Looking into directory " + dir.getAbsolutePath());
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.toLowerCase().endsWith(".html");
                    }
                });
                if (files != null && files.length > 0) {
                    boolean showIndex = files.length > 1;
                    Sink sink = getSink();
                    if (sink != null) {
                        StringBuilder buffer = new StringBuilder();
                        sink.head();
                        sink.title();
                        sink.text("Camel Endpoints");
                        sink.title_();
                        sink.rawText("<style>\n"
                                + "th, td {\n"
                                + "  text-align:left;\n\n"
                                + "}\n"
                                + "</style>\n");
                        sink.head_();

                        sink.body();
                        sink.section1();
                        sink.sectionTitle1();
                        sink.text("Camel Endpoints");
                        sink.sectionTitle1_();

                        if (showIndex) {
                            sink.list();
                        }
                        for (File file : files) {
                            getLog().info("found " + file.getAbsolutePath());

                            String linkName = file.getName();
                            if (linkName.endsWith(".html")) {
                                linkName = linkName.substring(0, linkName.length() - 5);
                            }
                            String endpointHtml = IOUtil.toString(new FileInputStream(file));
                            endpointHtml = extractBodyContents(endpointHtml);

                            if (showIndex) {
                                sink.listItem();
                                sink.link("#" + linkName);
                                sink.text(linkName);
                                sink.link_();
                                sink.listItem_();

                                buffer.append("<a name='" + linkName + "'>\n");
                                buffer.append(endpointHtml);
                                buffer.append("</a>\n");
                            } else {
                                sink.section1_();

                                sink.section2();
                                sink.rawText(endpointHtml);
                                sink.section2_();
                            }
                        }
                        if (showIndex) {
                            sink.list_();
                            sink.section1_();

                            sink.section2();
                            sink.rawText(buffer.toString());
                            sink.section2_();
                        }
                        sink.body_();
                        sink.flush();
                        sink.close();
                    }
                }
            }
        } catch (Exception e) {
            final MavenReportException ex = new MavenReportException(e.getMessage());
            ex.initCause(e.getCause());
            throw ex;
        }
    }

    /**
     * Extracts the content between <body> and </body>
     */
    protected String extractBodyContents(String html) {
        String body = "<body>";
        int idx = html.indexOf(body);
        if (idx > 0) {
            html = html.substring(idx + body.length());
        }
        idx = html.lastIndexOf("</body>");
        if (idx > 0) {
            html = html.substring(0, idx);
        }
        return html;
    }


/*
    public void execute() throws MojoExecutionException {
        this.execute(this.buildDirectory, Locale.getDefault());
        try {
            writeIndexHtmlFile(outputDirectory, "index.html", indexHtmlContent);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed: " + e, e);
        }
    }

    */
/**
 * Executes DOT generator.
 *
 * @param outputDir report output directory.
 * @param locale report locale.
 * @throws org.apache.maven.plugin.MojoExecutionException if there were any execution errors.
 *//*

    protected void execute(final File outputDir, final Locale locale) throws MojoExecutionException {
        outputDir.mkdirs();

        List<File> files = new ArrayList<File>();
        appendFiles(files, outputDirectory);

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
            log.info("DOT generation disabled.");
            return null;
        } else {
            if (dotHelpExitCode() != 0) {
                log.info("'dot -?' execution failed so DOT generation disabled.");
                return null;
            }
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

    private int dotHelpExitCode() throws CommandLineException {
        Commandline cl = new Commandline();
        cl.setExecutable(executable);
        cl.createArg().setValue("-?");

        CommandLineUtils.StringStreamConsumer stdout = new CommandLineUtils.StringStreamConsumer();
        CommandLineUtils.StringStreamConsumer stderr = new CommandLineUtils.StringStreamConsumer();

        return CommandLineUtils.executeCommandLine(cl, stdout, stderr);
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
*/

    /**
     * Gets resource bundle for given locale.
     *
     * @param locale locale
     * @return resource bundle
     */
    protected ResourceBundle getBundle(final Locale locale) {
        return ResourceBundle
                .getBundle("camel-package-maven-plugin", locale, this.getClass().getClassLoader());
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

    public String getDescription(final Locale locale) {
        return getBundle(locale).getString("report.endpoint.description");
    }

    /**
     * @see org.apache.maven.reporting.MavenReport#getName(java.util.Locale)
     */
    public String getName(final Locale locale) {
        return getBundle(locale).getString("report.endpoint.name");
    }

    public String getOutputName() {
        return "camelEndpoints";
    }
}
