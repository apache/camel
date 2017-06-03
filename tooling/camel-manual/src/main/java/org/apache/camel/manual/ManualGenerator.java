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
package org.apache.camel.manual;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import org.ccil.cowan.tagsoup.Parser;
import org.ccil.cowan.tagsoup.XMLWriter;

public class ManualGenerator {

    String page;
    String output;
    String head;
    String version;
    String targetDir;
    String skip;
    
    public ManualGenerator(String[] args) {
        page = args[0];
        output = args[1];
        version = args[2];
        head = args[3];
        targetDir = args[4];
        skip = args[5];
    }
    
    public void run() {
        try {
            if (doGenerate()) {
                String content = grabBodyContent();
                storeHTMLFile(content);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private void storeHTMLFile(String content) throws IOException {
        String replaceToken = "<h3 id=\"replaceme\">.*</h3>";
        String replaceValue = "<h3>Version " + version + "</h3>";
        
        File outFile = new File(output);
        outFile.getParentFile().mkdirs();
        PrintWriter out = new PrintWriter(new BufferedOutputStream(new FileOutputStream(outFile)));
        out.println("<html>");
        out.println("<head>");
        if (head != null) {
            out.println(head);
        }
        out.println("</head>");
        
        if (replaceToken != null && replaceValue != null) {
            content = content.replaceAll(replaceToken, replaceValue);
        }
        
        out.print("<body>");
        out.print(content);
        out.println("</body>");
        out.close();
    }
    private boolean doGenerate() throws MalformedURLException, IOException {
        if (skip.equalsIgnoreCase("true")) {
            // we don't want to generate the manual here
            return false;
        }
        URL url = new URL(page);
        File file = new File(targetDir, ".manualCache-" + url.getFile().substring(1));
        if (file.exists()) {
            HttpURLConnection con = (HttpURLConnection)url.openConnection();
            con.setRequestMethod("HEAD");
            long date = con.getLastModified();
            
            FileReader reader = new FileReader(file);
            char chars[] = new char[1000];
            int i = reader.read(chars);
            reader.close();

            long lastDate = Long.parseLong(new String(chars, 0, i).trim());
            if (date <= lastDate) {
                return false;
            }
        }
        return true;
    }
    private String grabBodyContent() throws MalformedURLException, IOException {
        URL url = new URL(page);
        File file = new File(targetDir, ".manualCache-" + url.getFile().substring(1));
        
        try {
            HttpURLConnection con = (HttpURLConnection)url.openConnection();
            XMLReader parser = new Parser();
            parser.setFeature(Parser.namespacesFeature, false);
            parser.setFeature(Parser.namespacePrefixesFeature, false);
            parser.setProperty(Parser.schemaProperty, new org.ccil.cowan.tagsoup.HTMLSchema() {
                {
                    //problem with nested lists that the confluence {toc} macro creates
                    elementType("ul", M_LI, M_BLOCK | M_LI, 0);
                }
            });
            
            StringWriter w = new StringWriter();
            XMLWriter xmlWriter = new XMLWriter(w) {
                int inDiv = Integer.MAX_VALUE;
                int count;
                public void characters(char ch[], int start, int len)
                    throws SAXException {
                    if (inDiv <= count) {
                        super.characters(ch, start, len);
                    }
                }
                public void startElement(String uri, String localName, String qName, Attributes atts)
                    throws SAXException {
                    count++;
                    if ("div".equalsIgnoreCase(qName)
                        && "wiki-content maincontent".equalsIgnoreCase(atts.getValue("class"))) {
                        inDiv = count;
                    }
                    if (inDiv <= count) {
                        super.startElement(uri, localName, qName, atts);
                    }
                }
                public void endElement(String uri, String localName, String qName) throws SAXException {
                    if (inDiv <= count) {
                        super.endElement(uri, localName, qName);
                    }
                    count--;
                    if (inDiv > count) {
                        inDiv = Integer.MAX_VALUE;
                    }
                }
            };
            xmlWriter.setOutputProperty(XMLWriter.OMIT_XML_DECLARATION, "yes");
            xmlWriter.setOutputProperty(XMLWriter.METHOD, "html");
            parser.setContentHandler(xmlWriter);
            long date = con.getLastModified();
            parser.parse(new InputSource(new BufferedInputStream(con.getInputStream())));

            
            FileWriter writer = new FileWriter(file);
            writer.write(Long.toString(date));
            writer.close();
            return w.toString();
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException("Failed", e);
        }
    }
    

    /**
     * @param args
     */
    public static void main(String[] args) {
        /*args = new String[] {
            "http://camel.apache.org/book-in-one-page.html",
            "/tmp/foo.html",
            "1.0",
            ""
        };*/
        new ManualGenerator(args).run();
    }

}
