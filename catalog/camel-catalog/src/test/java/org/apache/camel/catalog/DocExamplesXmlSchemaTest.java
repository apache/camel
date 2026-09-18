/*
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
package org.apache.camel.catalog;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The XML examples of the documentation bundled in the catalog must validate against the XML schemas bundled next to
 * them (CAMEL-24774): a {@code <camelContext>} against camel-spring.xsd as Spring XML validates it, the xml-io roots
 * ({@code <camel>}, {@code <routes>}...) against camel-xml-io.xsd, and any other Camel element written on its own
 * ({@code <route>}, {@code <onException>}, {@code <marshal>}...) as the global element it is in camel-spring.xsd, so a
 * fragment is checked without a wrapper imposing an element order the page never meant.
 * <p/>
 * The examples are what people copy: a Camel 1.x namespace, an attribute renamed in Camel 3, a tag closed by another
 * tag or a {@code &} in a URI fails the build here instead of in the reader's application.
 */
class DocExamplesXmlSchemaTest {

    private static final Pattern XML_BLOCK = Pattern.compile("\\[source,xml\\]\\n-{4,}\\n(.*?)\\n-{4,}", Pattern.DOTALL);
    private static final Pattern CALLOUT = Pattern.compile("\\s*<\\d+>\\s*$", Pattern.MULTILINE);
    private static final Pattern XML_DECLARATION = Pattern.compile("<\\?xml[^>]*\\?>");
    private static final Pattern CAMEL_CONTEXT
            = Pattern.compile("<(\\w+:)?camelContext\\b.*?</(\\w+:)?camelContext>", Pattern.DOTALL);
    /**
     * An element that makes a block a Camel example rather than a payload, a Maven POM, an XSLT or a configuration file
     * of another project which happens to share element names with the Camel schema.
     */
    private static final Pattern CAMEL_SIGNATURE = Pattern.compile("<(camelContext|routes|route|from|to|toD|"
                                                                   + "marshal|unmarshal|dataFormats|rest|restConfiguration|"
                                                                   + "routeTemplate|templatedRoute|routeConfiguration|"
                                                                   + "onException|errorHandler|intercept|interceptFrom|"
                                                                   + "interceptSendToEndpoint|onCompletion|setHeader|setBody|"
                                                                   + "setProperty|setVariable|choice|filter|split|aggregate|"
                                                                   + "multicast|recipientList|wireTap|log|process|transform|"
                                                                   + "convertBodyTo|validate|enrich|pollEnrich|threads|threadPool|"
                                                                   + "propertyPlaceholder|globalOptions|streamCaching|jmxAgent|"
                                                                   + "sslContextParameters|xpath|simple|csimple|jsonpath|jq|"
                                                                   + "xtokenize|tokenize|xquery|groovy|datasonnet|mvel|ognl)"
                                                                   + "[\\s/>]");

    private static final String SPRING_NS = "http://camel.apache.org/schema/spring";
    private static final String XML_IO_NS = "http://camel.apache.org/schema/xml-io";
    private static final String SPRING_BEANS_NS = "http://www.springframework.org/schema/beans";

    /** The roots of an xml-io file, validated against camel-xml-io.xsd. */
    private static final Set<String> XML_IO_ROOTS
            = Set.of("camel", "routes", "rests", "routeTemplates", "templatedRoutes", "routeConfigurations");

    /**
     * Top-level elements that are not judged on their own: a Spring {@code <bean>} and its {@code <property>} share
     * their names with Camel elements, and the rest are Maven, XSLT or payload samples.
     */
    private static final Set<String> IGNORED_ROOTS = Set.of("bean", "beans", "property", "dependency", "dependencies",
            "dependencyManagement", "plugin", "plugins", "build", "project", "extensions");

    /**
     * Examples the schemas cannot judge, by page and a text found in the example: an elided {@code <beans ...>} start
     * tag, a Spring bean next to route fragments, and the {@code <namespace>} child of an expression, which the model
     * and the xml-io parser support but no generated schema can express next to the expression text (JAXB
     * {@code @XmlValue}), so Spring XML declares the namespaces as {@code xmlns:} attributes instead; and the endpoint
     * page's unescaped {@code &}, which is there to show the error it causes.
     */
    private static final Map<String, String> EXAMPLES_SKIPPED = Map.of(
            "xmlsecurity-sign-component", "<bean id=\"xadesProperties\"",
            "xmlsecurity-verify-component", "<bean id=\"xadesProperties\"",
            "spring-summary", "<beans xmlns=\"http://www.springframework.org/schema/beans\"",
            "split-eip", "<namespace key=",
            "xtokenize-language", "<namespace key=",
            "endpoint", "paramA=1&paramB=2");

    private static CamelCatalog catalog;
    private static Schema springSchema;
    private static Schema xmlIoSchema;
    private static Set<String> springGlobalElements;

    private record DocExamples(int examples, List<String> failures) {
    }

    @BeforeAll
    static void setup() throws Exception {
        catalog = new DefaultCamelCatalog();
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        springSchema = factory.newSchema(new StreamSource(new StringReader(catalog.springSchemaAsXml())));
        xmlIoSchema = factory.newSchema(new StreamSource(new StringReader(catalog.xmlIoSchemaAsXml())));
        springGlobalElements = globalElements(catalog.springSchemaAsXml());
    }

    @Test
    void everyXmlExampleOfTheDocumentationValidates() throws Exception {
        List<String> pages = new ArrayList<>(catalog.findDocNames());

        DocExamples result = validate(pages);

        assertTrue(result.examples() > 1500, "XML examples found in the documentation: " + result.examples());
        assertTrue(result.failures().isEmpty(),
                "Documentation XML examples that do not validate:\n  " + String.join("\n  ", result.failures()));
    }

    @Test
    void everyXmlExampleOfTheUserManualValidates() throws Exception {
        Map<String, String> pages = UserManualPages.currentPages();
        assumeTrue(!pages.isEmpty(), "the user manual is only checked inside the Camel source tree");

        DocExamples result = validate(pages);

        assertTrue(result.examples() > 200, "XML examples found in the user manual: " + result.examples());
        assertTrue(result.failures().isEmpty(),
                "User manual XML examples that do not validate:\n  " + String.join("\n  ", result.failures()));
    }

    @Test
    void theCheckSeesWhatItIsFor() {
        // the fragments are judged as the global elements of the schema; the set must be the real one
        assertTrue(springGlobalElements.size() > 200 && springGlobalElements.contains("route")
                && springGlobalElements.contains("onException") && springGlobalElements.contains("marshal"),
                "global elements of camel-spring.xsd: " + springGlobalElements.size());
        // the classes of mistakes the check exists for
        assertTrue(validateBlock("<camelContext xmlns=\"http://activemq.apache.org/camel/schema/spring\">\n"
                                 + "<route><from uri=\"direct:a\"/><to uri=\"mock:b\"/></route></camelContext>")
                .toString().contains("camelContext"), "the Camel 1.x namespace");
        assertTrue(!validateBlock("<from>seda:a?size=1</from>").isEmpty(), "a from with text content");
        assertTrue(!validateBlock("<onCompletion executorServiceRef=\"x\"><to uri=\"mock:a\"/></onCompletion>").isEmpty(),
                "a Camel 2 attribute");
        assertTrue(!validateBlock("<route><from uri=\"direct:a\"/><to uri=\"mock:b?x=1&y=2\"/></route>").isEmpty(),
                "an unescaped ampersand");
        assertTrue(!validateBlock("<route><from uri=\"direct:a\"/><marshal><jaxb/></marshal></route>").isEmpty(),
                "a missing required attribute");
        assertTrue(!validateBlock("<onException><redeliveryPolicy maximumRedeliveries=\"1\"/>"
                                  + "<exception>java.io.IOException</exception></onException>")
                .isEmpty(),
                "the element order Spring XML enforces");
        // and what it leaves alone
        assertTrue(validateBlock("<route id=\"a\"><from uri=\"direct:a\"/><to uri=\"mock:b\"/></route>\n"
                                 + "<onException><exception>java.io.IOException</exception><to uri=\"mock:e\"/></onException>")
                .isEmpty(), "valid fragments in any order");
        assertTrue(validateBlock("<routes><route><from uri=\"direct:a\"/><to uri=\"mock:b\"/></route></routes>").isEmpty(),
                "an xml-io root");
        assertTrue(validateBlock("<mapper><delete id=\"x\" parameterType=\"int\">delete from t</delete></mapper>").isEmpty(),
                "a MyBatis mapper sharing element names");
        assertTrue(validateBlock("<bean id=\"a\" class=\"com.foo.A\"/>\n<from uri=\"direct:a\"/> <1>\n...").isEmpty(),
                "a Spring bean, a callout and a placeholder line");
    }

    @Test
    void theSkippedPagesStillExist() throws Exception {
        Map<String, String> manual = UserManualPages.pages();
        for (String page : EXAMPLES_SKIPPED.keySet()) {
            assertTrue(catalog.asciiDoc(page) != null || manual.containsKey(page),
                    "the skipped page " + page + " is gone, drop the entry");
        }
    }

    private static DocExamples validate(List<String> pages) throws Exception {
        Map<String, String> docs = new LinkedHashMap<>();
        for (String page : pages) {
            String doc = catalog.asciiDoc(page);
            if (doc != null) {
                docs.put(page, doc);
            }
        }
        return validate(docs);
    }

    private static DocExamples validate(Map<String, String> pages) {
        int examples = 0;
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, String> entry : pages.entrySet()) {
            String page = entry.getKey();
            String doc = entry.getValue();
            int n = 0;
            Matcher m = XML_BLOCK.matcher(doc);
            while (m.find()) {
                n++;
                String xml = m.group(1);
                String skipped = EXAMPLES_SKIPPED.get(page);
                if (skipped != null && xml.contains(skipped)) {
                    continue;
                }
                int line = 1 + countLines(doc, m.start(1));
                for (String error : validateBlock(xml)) {
                    failures.add(page + ".adoc:" + line + " example " + n + ": " + error);
                }
                examples++;
            }
        }
        return new DocExamples(examples, failures);
    }

    /**
     * Validates one XML example and returns what is wrong with it (empty when it validates or is not an example the
     * schemas can judge).
     */
    static List<String> validateBlock(String xml) {
        List<String> errors = new ArrayList<>();
        String text = clean(xml);
        if (text.isBlank() || !CAMEL_SIGNATURE.matcher(text).find()) {
            // a payload, a POM, an XSLT: not something the Camel schemas describe
            return errors;
        }
        if (text.contains("<camelContext") || text.contains(":camelContext")) {
            Matcher cm = CAMEL_CONTEXT.matcher(text);
            while (cm.find()) {
                String context = cm.group(0);
                if (cm.group(1) != null) {
                    String prefix = cm.group(1).substring(0, cm.group(1).length() - 1);
                    if (!context.contains("xmlns:" + prefix + "=")) {
                        context = context.replaceFirst("<" + prefix + ":camelContext",
                                "<" + prefix + ":camelContext xmlns:" + prefix + "=\"" + SPRING_NS + "\"");
                    }
                } else {
                    context = withDefaultNamespace(context, "camelContext", SPRING_NS);
                }
                validate(context, springSchema, errors);
            }
            return errors;
        }

        List<Element> roots;
        try {
            roots = topLevelElements(text);
        } catch (SAXParseException e) {
            errors.add("not well-formed XML: " + e.getMessage());
            return errors;
        } catch (Exception e) {
            errors.add("not well-formed XML: " + e);
            return errors;
        }
        for (Element root : roots) {
            String name = root.getNodeName();
            String local = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
            String ns = root.getAttribute("xmlns");
            if (SPRING_BEANS_NS.equals(ns) || IGNORED_ROOTS.contains(local) || name.contains(":")) {
                // a Spring beans file, a Maven or payload sample, or a prefixed element of another schema
                continue;
            }
            String element = serialize(root);
            if (element == null) {
                // an undeclared prefix (prop:, camel:...) the page declares elsewhere, nothing to judge
                continue;
            }
            if (XML_IO_ROOTS.contains(local)) {
                validate(withDefaultNamespace(element, local, XML_IO_NS), xmlIoSchema, errors);
            } else if (springGlobalElements.contains(local)) {
                validate(withDefaultNamespace(element, local, SPRING_NS), springSchema, errors);
            }
        }
        return errors;
    }

    private static void validate(String document, Schema schema, List<String> errors) {
        try {
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new StringReader(document)));
        } catch (SAXParseException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("is not bound")) {
                // a fragment using a prefix its page declares elsewhere (camel:, prop:, xsl:...), nothing to judge
                return;
            }
            errors.add(msg);
        } catch (Exception e) {
            errors.add(e.toString());
        }
    }

    /** Drops the XML declaration, the placeholder lines and the AsciiDoc callouts, which are not part of the XML. */
    static String clean(String xml) {
        String text = XML_DECLARATION.matcher(xml).replaceAll("");
        text = CALLOUT.matcher(text).replaceAll("");
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.equals("...") || t.equals("…")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /** Adds the default namespace to the root element when the example does not declare one. */
    private static String withDefaultNamespace(String document, String root, String ns) {
        int end = document.indexOf('>');
        String startTag = end > 0 ? document.substring(0, end) : document;
        if (startTag.contains("xmlns=")) {
            return document;
        }
        return document.replaceFirst("<" + root + "\\b", "<" + root + " xmlns=\"" + ns + "\"");
    }

    /** The top-level elements of a fragment, parsed without namespaces so undeclared prefixes do not matter. */
    private static List<Element> topLevelElements(String fragment) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document dom = builder.parse(new InputSource(new StringReader("<wrap>" + fragment + "</wrap>")));
        List<Element> answer = new ArrayList<>();
        NodeList children = dom.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                answer.add((Element) child);
            }
        }
        return answer;
    }

    private static String serialize(Element element) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter sw = new StringWriter();
            transformer.transform(new DOMSource(element), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            // an attribute with a prefix the fragment does not declare cannot be serialized
            return null;
        }
    }

    /**
     * The names of the global elements of a schema, the elements an example can start with: the {@code xs:element}
     * children of the {@code xs:schema} root, read from the parsed schema so the formatting of the generated file does
     * not matter.
     */
    private static Set<String> globalElements(String xsd) throws Exception {
        Set<String> answer = new HashSet<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document schema = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xsd)));
        NodeList children = schema.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "element".equals(child.getLocalName())
                    && XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(child.getNamespaceURI())) {
                answer.add(((Element) child).getAttribute("name"));
            }
        }
        if (answer.isEmpty()) {
            throw new IllegalStateException("No global elements found in the schema, the fragments would not be judged");
        }
        return answer;
    }

    private static int countLines(String text, int end) {
        int n = 0;
        for (int i = 0; i < end; i++) {
            if (text.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }
}
