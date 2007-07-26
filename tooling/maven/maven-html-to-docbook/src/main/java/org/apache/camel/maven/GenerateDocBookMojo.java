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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;

import org.w3c.tidy.DOMElementImpl;
import org.w3c.tidy.Tidy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Goal which extracts the content of a wiki page and converts it to docbook
 * format
 * 
 * @goal htmlToDocbook
 * @phase process-sources
 */
public class GenerateDocBookMojo extends AbstractMojo {

	/**
	 * Base URL.
	 * 
	 * @parameter expression="${baseURL}"
	 *            default-value="http://activemq.apache.org/camel/"
	 * @required
	 */
	private String baseURL;

	/**
	 * List of resources
	 * 
	 * @parameter
	 */
	private String[] resources;

	/**
	 * List of author's fullname
	 * 
	 * @parameter
	 */
	private String[] authors;

	/**
	 * Location of the xsl file.
	 * 
	 * @parameter expression="${configDirectory}"
	 *           
	 */
	private String xslFile;

	/**
	 * Location of the output directory.
	 * 
	 * @parameter expression="${project.build.directory}/docbkx/docbkx-source"
	 */
	private String outputPath;

	/**
	 * Location of the output directory for wiki source.
	 * 
	 * @parameter expression="${project.build.directory}/docbkx/wiki-source"
	 */
	private String wikiOutputPath;

	/**
	 * @parameter expression="${title}"
	 * @required
	 */
	private String title;

	/**
	 * @parameter expression="${subtitle}"
	 */
	private String subtitle;

	/**
	 * @parameter expression="${mainFilename}" default-value="manual"
	 * @required
	 */
	private String mainFilename;

	/**
	 * @parameter expression="${version}" default-value="${project.version}"
	 */
	private String version;

	/**
	 * @parameter expression="${legalNotice}"
	 */
	private String legalNotice;

	/**
	 * Location of image files.
	 * 
	 * @parameter expression="${project.build.directory}/site/book/images"
	 *            
	 */
	private String imageLocation;

	private String chapterId;

	private static final transient Log log = LogFactory
			.getLog(GenerateDocBookMojo.class);

	public void execute() throws MojoExecutionException {
		File outputDir = new File(outputPath);
		File wikiOutputDir = new File(wikiOutputPath);
		File imageDir = new File(imageLocation);
		if (!outputDir.exists()) {
			outputDir.mkdirs();
			imageDir.mkdirs();
			wikiOutputDir.mkdirs();
		}
		this.createMainXML();

		for (int i = 0; i < resources.length; ++i) {
			this.setChapterId(removeExtension(resources[i]));

			process(resources[i]);
		}

	}

	/**
	 * Extract the wiki content and tranform it into docbook format
	 * 
	 * @param resource
	 */
	public void process(String resource) {

		Tidy tidy = new Tidy();
		ByteArrayOutputStream out = null;
		BufferedOutputStream output = null;
		BufferedOutputStream wikiOutput = null;
		StreamSource streamSource = null;

		tidy.setXmlOut(true);
		try {
			out = new ByteArrayOutputStream();
			URL u = new URL(baseURL + resource);
			Document doc = tidy.parseDOM(
					new BufferedInputStream(u.openStream()), out);
			out.close();
			// let's extract the div element with class="wiki-content
			// maincontent"
			NodeList nodeList = doc.getElementsByTagName("div");
			for (int i = 0; i < nodeList.getLength(); ++i) {
				Node node = nodeList.item(i);

				NamedNodeMap nm = node.getAttributes();
				Node attr = nm.getNamedItem("class");

				if (attr != null
						&& attr.getNodeValue().equalsIgnoreCase(
								"wiki-content maincontent")) {
					downloadImages(node);
					// These attributes will be used by xsl to
					Element element = (Element) node;
					element.setAttribute("chapterId", chapterId);
					element.setAttribute("baseURL", baseURL);
					element.setAttribute("imageLocation", "../images/");

					DOMSource source = new DOMSource(
							processH2Section(doc, node));

					output = new BufferedOutputStream(new FileOutputStream(
							outputPath + File.separator
									+ removeExtension(resource) + ".xml"));
					StreamResult result = new StreamResult(output);
					TransformerFactory tFactory = TransformerFactory
							.newInstance();
					if (xslFile != null && !xslFile.trim().equals("")) {
						streamSource = new StreamSource(xslFile);
					} else {
						InputStream xslStream = getClass().getResourceAsStream(
								"/docbook.xsl");
						streamSource = new StreamSource(xslStream);
					}

					Transformer transformer = tFactory
							.newTransformer(streamSource);
					transformer.transform(source, result);

					// generate the wiki source for debugging
					wikiOutput = new BufferedOutputStream(new FileOutputStream(
							wikiOutputPath + File.separator
									+ removeExtension(resource) + ".html"));
					result = new StreamResult(wikiOutput);
					transformer = tFactory.newTransformer();
					transformer.transform(source, result);

					break;
				}

			}

		} catch (Exception e) {
			log.debug("Exception processing wiki content", e);
		} finally {
			try {
				if (output != null)
					output.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				log.debug("Exception closing output stream", e);
			}
		}
	}

	/*
	 *  create the main docbook xml file 
	 */
	public void createMainXML() {
		try {

			PrintWriter out = new PrintWriter(new FileWriter(outputPath
					+ File.separator + mainFilename + ".xml"));

			out
					.println("<!DOCTYPE book PUBLIC \"-//OASIS//DTD DocBook XML V4.4//EN\" \"http://www.oasis-open.org/docbook/xml/4.4/docbookx.dtd\" ");
			out.println("[");

			for (int i = 0; i < resources.length; ++i) {
				out.println("<!ENTITY " + removeExtension(resources[i])
						+ " SYSTEM \"" + removeExtension(resources[i])
						+ ".xml\">");
			}

			out.println("]>");
			out.println("<book>");
			out.println("<bookinfo>");
			out.println("<title>" + title + "</title>");
			out.println("<subtitle>" + subtitle + "</subtitle>");
			out.println("<releaseinfo>" + version + "</releaseinfo>");
			out.println(" <authorgroup>");
			if (authors != null) {
				for (int i = 0; i < authors.length; ++i) {
					StringTokenizer name = new StringTokenizer(authors[i]);
					String fname = name.nextToken();
					String lname = "";
					if (name.hasMoreTokens()) {
						lname = name.nextToken();
					}
					out.println("<author>");
					out.println("<firstname>" + fname + "</firstname>");
					out.println("<surname>" + lname + "</surname>");
					out.println("</author>");

				}
			}

			out.println("</authorgroup>");
			out.println("<legalnotice>");
			if (legalNotice != null && legalNotice.length() > 0) {
				out.println("<para>");
				out.println(legalNotice);
				out.println("</para>");
			} else {
				out
						.println("<para>Licensed to the Apache Software Foundation (ASF) under one or more");
				out
						.println("contributor license agreements. See the NOTICE file distributed with");
				out
						.println("this work for additional information regarding copyright ownership. The");
				out
						.println("ASF licenses this file to You under the Apache License, Version 2.0 (the");
				out
						.println("\"License\"); you may not use this file except in compliance with the");
				out
						.println("License. You may obtain a copy of the License at</para>");
				out
						.println("<para>http://www.apache.org/licenses/LICENSE-2.0</para>");
				out
						.println("<para>Unless required by applicable law or agreed to in writing,");
				out
						.println(" software distributed under the License is distributed on an \"AS IS\"");
				out
						.println("BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or");
				out
						.println("implied. See the License for the specific language governing permissions");
				out.println("and limitations under the License.</para>");
			}

			out.println("</legalnotice>");
			out.println("</bookinfo>");
			out.println("<toc></toc>");

			for (int i = 0; i < resources.length; ++i) {
				out.println("&" + removeExtension(resources[i]) + ";");
			}

			out.println("</book>");
			out.flush();
			out.close();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			log.debug("Exception in creating manual.xml file", e);
		}
	}

	public void downloadImages(Node node) {
		List<String> imageList = getImageUrls(node);
		Iterator<String> iter = imageList.iterator();
		while (iter.hasNext()) {
			String imageUrl = (String) iter.next();
			String imageFile = "imageFile";

			//check if url path is relative
			if (imageUrl.indexOf("http://") < 0) {
				imageUrl = baseURL + imageUrl;
			}
			try {

				URL url = new URL(imageUrl);
				StringTokenizer st = new StringTokenizer(url.getFile(), "/");
				while (st.hasMoreTokens()) {
					imageFile = st.nextToken();
				}

				URLConnection connection = url.openConnection();
				InputStream stream = connection.getInputStream();
				BufferedInputStream in = new BufferedInputStream(stream);
				FileOutputStream file = new FileOutputStream(imageLocation
						+ File.separator + imageFile);
				BufferedOutputStream out = new BufferedOutputStream(file);
				int i;
				while ((i = in.read()) != -1) {
					out.write(i);
				}
				out.flush();
			} catch (Exception e) {
				log.debug("Exception in downloading image " + imageFile, e);
			}

		}
	}

	public List<String> getImageUrls(Node node) {
		List<String> list = new ArrayList<String>();
		DOMElementImpl doc = (DOMElementImpl) node;
		NodeList imageList = doc.getElementsByTagName("img");

		if (imageList != null) {
			for (int i = 0; i < imageList.getLength(); ++i) {
				Node imageNode = imageList.item(i);

				NamedNodeMap nm = imageNode.getAttributes();
				Node attr = nm.getNamedItem("src");
				if (attr != null) {
					list.add(attr.getNodeValue());
				}

			}
		}
		return list;
	}

	public String getChapterId() {
		return chapterId;
	}

	public void setChapterId(String chapterId) {
		this.chapterId = chapterId;
	}

	public String removeExtension(String resource) {
		int index = resource.indexOf('.');
		return resource.substring(0, index);
	}

	/*
	 * creates a <h2_section> node  and place all nodes  after a <h2> node until another <h2> node is found. 
	 * This is so that we can divide chapter contents into section delimited by a <h2> node
	 */

	public Node processH2Section(Document doc, Node node) {
		NodeList nodeList = node.getChildNodes();
		Node h2Node = null;
		Node pNode = null;
		boolean firstInstanceOfH2 = false;

		for (int x = 0; x < nodeList.getLength(); ++x) {
			Node node2 = nodeList.item(x);

			if (node2 != null) {
				String nodes = node2.getNodeName();

				if (nodes.equalsIgnoreCase("h2")) {
					h2Node = node2.appendChild(doc.createElement("h2_section"));
				} else {
					//if first node is not a <p> or a h2 node, create a <p> node and place all succeeding nodes 
					//inside this node until a <p> or <h2> node is found
					if (x == 0 && !nodes.equalsIgnoreCase("p")
							&& !nodes.equalsIgnoreCase("h2")) {
						pNode = node
								.insertBefore(doc.createElement("p"), node2);
						x++;
						firstInstanceOfH2 = true;
					}
					if (firstInstanceOfH2) {
						if (node2 == node.getLastChild()) {
							pNode.appendChild(node2.cloneNode(true));
						} else {
							Node nextNode = node2.getNextSibling();
							pNode.appendChild(node2.cloneNode(true));
							if (nextNode.getNodeName().equalsIgnoreCase("h2")
									|| nextNode.getNodeName().equalsIgnoreCase(
											"p")) {
								firstInstanceOfH2 = false;
							}
						}

					}

					if (h2Node != null) {
						h2Node.appendChild(node2.cloneNode(true));
					}
				}

			}
		}

		//let's remove all  nodes that are not <h2> or <p> - they should already have been copied inside an <h2> or <p> node
		NodeList nodeList3 = node.getChildNodes();
		boolean afterH2 = false;
		for (int x = 0; x < nodeList3.getLength(); ++x) {
			Node node2 = nodeList3.item(x);
			if (node2.getNodeName().equalsIgnoreCase("h2") && !afterH2) {
				afterH2 = true;
			}

			if (node2 != null && !node2.getNodeName().equalsIgnoreCase("p")
					&& !node2.getNodeName().equalsIgnoreCase("h2")) {
				node.removeChild(node2);
				x--;
			}
		}
		return node;
	}
}
