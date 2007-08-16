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
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.Commandline.Argument;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Goal which extracts the content html page and converts to PDF using Prince
 * 
 * @goal confluenceToPDF
 * @phase process-sources
 */
public class ConfluenceToPDFMojo extends AbstractMojo {

    private static final transient Log LOG = LogFactory.getLog(ConfluenceToPDFMojo.class);

    /**
     * The URL to the confluence page to convert.
     * 
     * @parameter expression="${page}"
     *            default-value="http://cwiki.apache.org/confluence/display/CAMEL/Index"
     * @required
     */
    private String page;

    /**
     * Location of the work directory.
     * 
     * @parameter expression="${project.build.directory}/html-to-pdf"
     */
    private String workDir;

    /**
     * The output file name for the pdf.
     * 
     * @parameter expression="${pdf}"
     *            default-value="${project.build.directory}/html-to-pdf/${project.artifactId}-${project.version}.pdf"
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

    public void execute() throws MojoExecutionException {
        File outputDir = new File(workDir);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        try {

            // Download
            String content = downloadContent();
            // Store
            storeHTMLFile(content);
            // Run Prince
            getLog().info("Converting to PDF with prince...");
            Commandline cl = new Commandline("prince");
            Argument arg = new Argument();
            arg.setValue(getHTMLFileName());
            cl.addArg(arg);
            arg = new Argument();
            arg.setValue(getPDFFileName());
            cl.addArg(arg);

            StreamConsumer out = new StreamConsumer() {
                public void consumeLine(String line) {
                    System.out.println("prince: " + line);
                }
            };

            int rc = CommandLineUtils.executeCommandLine(cl, out, out);
            if (rc == 0) {
                getLog().info("Stored: " + getPDFFileName());
            } else {
                throw new MojoExecutionException("PDF Conversion failed rc=" + rc);
            }

        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Download of '" + page + "' failed: " + e.getMessage(), e);
        }
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
        return getPDFFileName() + ".html";
    }

    private String downloadContent() throws MalformedURLException, IOException, TransformerFactoryConfigurationError, TransformerConfigurationException, TransformerException,
        UnsupportedEncodingException, MojoExecutionException {

        getLog().info("Downloading: " + page);
        URL url = new URL(page);
        Document doc = new Tidy().parseDOM(new BufferedInputStream(url.openStream()), new ByteArrayOutputStream());

        NodeList nodeList = doc.getElementsByTagName("div");
        for (int i = 0; i < nodeList.getLength(); ++i) {
            Node node = nodeList.item(i);
            NamedNodeMap nm = node.getAttributes();
            Node attr = nm.getNamedItem("class");
            if (attr != null && attr.getNodeValue().equalsIgnoreCase("wiki-content")) {
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
        throw new MojoExecutionException("The '" + page + "' page did not have a <div class=\"wiki-content\"> element.");
    }

}
