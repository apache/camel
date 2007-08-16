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
package org.apache.camel.maven;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.tidy.Tidy;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.codehaus.plexus.util.cli.Commandline.Argument;

/**
 * Goal which extracts the content div from the html page and converts to PDF
 * using Prince
 * 
 * @goal compile
 * @phase compile
 */
public class HtmlToPdfMojo extends AbstractMojo {

    /**
     * The URL to the confluence page to convert.
     * 
     * @parameter expression="${page}"
     *            default-value="http://cwiki.apache.org/confluence/display/CAMEL/Index"
     * @required
     */
    private String page;

    /**
     * The output file name for the pdf.
     * 
     * @parameter expression="${pdf}"
     *            default-value="${project.build.directory}/site/manual/${project.artifactId}-${project.version}.pdf"
     */
    private String pdf;

    /**
     * The css style sheets that should be linked.
     * 
     * @parameter
     */
    private String[] styleSheets;

    /**
     * Content that should be added in the head element of the html file.
     * 
     * @parameter
     */
    private String head;

    /**
     * The first div with who's class matches the contentDivClass will be
     * assumed to be the content section of the HTML and is what will be used as
     * the content in the PDF.
     * 
     * @parameter default-value="wiki-content"
     */
    private String contentDivClass = "wiki-content";

    /**
     * Arguments that should be passed to the prince html to pdf processor.
     * 
     * @parameter
     */
    private String[] princeArgs;

    /**
     * If there is an error converting the HTML to PDF should the build fail?
     * default to false since this requires the prince tool to be installed and
     * on the PATH of the system.
     * 
     * @parameter default-value="false"
     */
    private boolean errorOnConverionFailure;

    /**
     * If there is an error downloading the HTML should the build fail? default
     * to false since this usually requires the user to be online.
     * 
     * @parameter default-value="false"
     */
    private boolean errorOnDownloadFailure;

    /**
     * The maven project.
     * 
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * @component
     */
    private MavenProjectHelper projectHelper;

    /**
     * The type used when attaching the artifact to the deployment.
     * 
     * @parameter default-value="pdf"
     */
    private String type;

    /**
     * Classifier to add to the artifact generated.
     * 
     * @parameter
     */
    private String classifier;

    public void execute() throws MojoExecutionException {
        File outputDir = new File(pdf).getParentFile();
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        try {
            // Download
            String content = downloadContent();
            if (content == null) {
                return;
            }

            // Store
            storeHTMLFile(content);

            // Run Prince
            if (convert() == 0) {
                File pdfFile = new File(getPDFFileName());
                projectHelper.attachArtifact(project, type, classifier, pdfFile);
            }

        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Download of '" + page + "' failed: " + e.getMessage(), e);
        }
    }

    private int convert() throws CommandLineException, MojoExecutionException {
        getLog().info("Converting to PDF with prince...");
        Commandline cl = new Commandline("prince");
        Argument arg;

        if (princeArgs != null) {
            for (int i = 0; i < princeArgs.length; i++) {
                arg = new Argument();
                arg.setValue(princeArgs[i]);
                cl.addArg(arg);
            }
        }

        arg = new Argument();
        arg.setValue(getHTMLFileName());
        cl.addArg(arg);
        arg = new Argument();
        arg.setValue(getPDFFileName());
        cl.addArg(arg);

        StreamConsumer out = new StreamConsumer() {
            public void consumeLine(String line) {
                getLog().info("[prince] " + line);
            }
        };

        int rc = CommandLineUtils.executeCommandLine(cl, out, out);
        if (rc == 0) {
            getLog().info("Stored: " + getPDFFileName());
        } else {
            if (errorOnConverionFailure) {
                throw new MojoExecutionException("PDF Conversion failed rc=" + rc);
            } else {
                getLog().error("PDF Conversion failed due to return code: " + rc);
            }
        }
        return rc;
    }

    private String getPDFFileName() {
        return pdf;
    }

    private void storeHTMLFile(String content) throws FileNotFoundException {
        PrintWriter out = new PrintWriter(new BufferedOutputStream(new FileOutputStream(getHTMLFileName())));
        out.println("<html>");
        out.println("<head>");
        out.println("   <base href=\"" + page + "\"/>");
        if (head != null) {
            out.println(head);
        }
        if (styleSheets != null) {
            for (int i = 0; i < styleSheets.length; i++) {
                out.println("   <link href=\"" + styleSheets[i] + "\" rel=\"stylesheet\" type=\"text/css\"/>");
            }
        }
        out.println("</head>");
        out.println("<body>" + content + "</body>");
        out.close();
        getLog().info("Stored: " + getHTMLFileName());
    }

    private String getHTMLFileName() {
        String name = getPDFFileName();
        if (name.endsWith(".pdf")) {
            name = name.substring(0, name.length() - 4);
        }
        return name + ".html";
    }

    private String downloadContent() throws IOException, TransformerFactoryConfigurationError, TransformerException, MojoExecutionException {

        getLog().info("Downloading: " + page);
        URL url = new URL(page);
        Tidy tidy = new Tidy();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        tidy.setErrout(new PrintWriter(result));
        Document doc;
        try {
            doc = tidy.parseDOM(new BufferedInputStream(url.openStream()), new ByteArrayOutputStream());
        } catch (Throwable e) {
            if (errorOnDownloadFailure) {
                getLog().debug(new String(result.toByteArray()), e);
                throw new MojoExecutionException("Download or validation of '" + page + "' failed: " + e);
            } else {
                getLog().debug(new String(result.toByteArray()), e);
                getLog().error("Download or validation of '" + page + "' failed: " + e);
                return null;
            }
        }

        NodeList nodeList = doc.getElementsByTagName("div");
        for (int i = 0; i < nodeList.getLength(); ++i) {
            Node node = nodeList.item(i);
            NamedNodeMap nm = node.getAttributes();
            Node attr = nm.getNamedItem("class");
            if (attr != null && attr.getNodeValue().equalsIgnoreCase(contentDivClass)) {
                // Write the wiki-content div to the content variable.
                ByteArrayOutputStream contentData = new ByteArrayOutputStream(1024 * 100);
                TransformerFactory tFactory = TransformerFactory.newInstance();
                Transformer transformer = tFactory.newTransformer();
                transformer.transform(new DOMSource(node), new StreamResult(contentData));
                String content = new String(contentData.toByteArray(), "UTF-8");
                content = content.substring(content.indexOf("<div"));
                return content;
            }
        }
        throw new MojoExecutionException("The '" + page + "' page did not have a <div class=\"" + contentDivClass + "\"> element.");
    }

}
