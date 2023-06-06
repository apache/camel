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
package org.apache.camel.xml.io;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Pattern;

/**
 * XML writer which emits nicely formatted documents.
 */
public class XMLWriter {

    private static final Pattern LOWERS = Pattern.compile("([\000-\037])");

    private final Writer writer;
    private final String lineIndenter;
    private final String lineSeparator;
    private final String encoding;
    private final String docType;
    private final Deque<String> elements = new ArrayDeque<>();
    private boolean tagInProgress;
    private int depth;
    private boolean readyForNewLine;
    private boolean tagIsEmpty;

    /**
     * @param writer not null
     */
    public XMLWriter(Writer writer) throws IOException {
        this(writer, null, null);
    }

    /**
     * @param writer       not null
     * @param lineIndenter could be null, but the normal way is some spaces.
     */
    public XMLWriter(Writer writer, String lineIndenter) throws IOException {
        this(writer, lineIndenter, null, null);
    }

    /**
     * @param writer   not null
     * @param encoding could be null or invalid.
     * @param doctype  could be null.
     */
    public XMLWriter(Writer writer, String encoding, String doctype) throws IOException {
        this(writer, null, encoding, doctype);
    }

    /**
     * @param writer       not null
     * @param lineIndenter could be null, but the normal way is some spaces.
     * @param encoding     could be null or invalid.
     * @param doctype      could be null.
     */
    public XMLWriter(Writer writer, String lineIndenter, String encoding, String doctype) throws IOException {
        this(writer, lineIndenter, null, encoding, doctype);
    }

    /**
     * @param writer        not null
     * @param lineIndenter  could be null, but the normal way is some spaces.
     * @param lineSeparator could be null, but the normal way is valid line separator ("\n" on UNIX).
     * @param encoding      could be null or invalid.
     * @param doctype       could be null.
     */
    public XMLWriter(Writer writer, String lineIndenter, String lineSeparator,
                     String encoding, String doctype) throws IOException {
        this.writer = writer;
        this.lineIndenter = lineIndenter != null ? lineIndenter : "    ";
        this.lineSeparator = validateLineSeparator(lineSeparator);
        this.encoding = encoding;
        this.docType = doctype;
        if (doctype != null || encoding != null) {
            writeDocumentHeaders();
        }
    }

    private static String validateLineSeparator(String lineSeparator) {
        String ls = lineSeparator != null ? lineSeparator : System.lineSeparator();
        if (!(ls.equals("\n") || ls.equals("\r") || ls.equals("\r\n"))) {
            throw new IllegalArgumentException("Requested line separator is invalid.");
        }
        return ls;
    }

    /**
     * {@inheritDoc}
     */
    public void startElement(String name) throws IOException {
        tagIsEmpty = false;
        finishTag();
        write("<");
        write(name);
        elements.addLast(name);
        tagInProgress = true;
        depth++;
        readyForNewLine = true;
        tagIsEmpty = true;
    }

    /**
     * {@inheritDoc}
     */
    public void writeText(String text) throws IOException {
        writeText(text, true);
    }

    /**
     * {@inheritDoc}
     */
    public void writeMarkup(String text) throws IOException {
        writeText(text, false);
    }

    private void writeText(String text, boolean escapeXml) throws IOException {
        readyForNewLine = false;
        tagIsEmpty = false;
        finishTag();
        if (escapeXml) {
            text = escapeXml(text);
        }
        write(unifyLineSeparators(text));
    }

    /**
     * Parses the given String and replaces all occurrences of '\n', '\r' and '\r\n' with the system line separator.
     *
     * @param  s                        a not null String
     * @return                          a String that contains only System line separators.
     * @throws IllegalArgumentException if ls is not '\n', '\r' and '\r\n' characters.
     * @since                           1.5.7
     */
    private String unifyLineSeparators(String s) {
        if (s == null) {
            return null;
        }
        int length = s.length();
        StringBuilder buffer = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            if (s.charAt(i) == '\r') {
                if ((i + 1) < length && s.charAt(i + 1) == '\n') {
                    i++;
                }
                buffer.append(lineSeparator);
            } else if (s.charAt(i) == '\n') {
                buffer.append(lineSeparator);
            } else {
                buffer.append(s.charAt(i));
            }
        }
        return buffer.toString();
    }

    private static String escapeXml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String escapeXmlAttribute(String text) {
        text = escapeXml(text)
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
        // Windows
        text = text.replace("\r\n", "&#10;");
        // Non printable characters
        text = LOWERS.matcher(text).replaceAll(r -> "&#" + Integer.toString(r.group(1).charAt(0)) + ";");
        return text;
    }

    /**
     * {@inheritDoc}
     */
    public void addAttribute(String key, String value) throws IOException {
        write(" ");
        write(key);
        write("=\"");
        write(escapeXmlAttribute(value));
        write("\"");
    }

    /**
     * {@inheritDoc}
     */
    public void endElement(String name) throws IOException {
        depth--;

        if (tagIsEmpty) {
            write("/");
            readyForNewLine = false;
            finishTag();
            elements.removeLast();
        } else {
            finishTag();
            write("</" + elements.removeLast() + ">");
        }

        readyForNewLine = true;
    }

    /**
     * Write a string to the underlying writer
     */
    private void write(String str) throws IOException {
        getWriter().write(str);
    }

    private void finishTag() throws IOException {
        if (tagInProgress) {
            write(">");
        }
        tagInProgress = false;
        if (readyForNewLine) {
            endOfLine();
        }
        readyForNewLine = false;
        tagIsEmpty = false;
    }

    /**
     * Get the string used as line indenter
     *
     * @return the line indenter
     */
    protected String getLineIndenter() {
        return lineIndenter;
    }

    /**
     * Get the string used as line separator or LS if not set.
     *
     * @return the line separator
     * @see    System#lineSeparator()
     */
    protected String getLineSeparator() {
        return lineSeparator;
    }

    /**
     * Write the end of line character (using specified line separator) and start new line with indentation
     *
     * @see #getLineIndenter()
     * @see #getLineSeparator()
     */
    protected void endOfLine() throws IOException {
        write(getLineSeparator());
        for (int i = 0; i < getDepth(); i++) {
            write(getLineIndenter());
        }
    }

    private void writeDocumentHeaders() throws IOException {
        write("<?xml version=\"1.0\"");
        if (getEncoding() != null) {
            write(" encoding=\"" + getEncoding() + "\"");
        }
        write("?>");
        endOfLine();
        if (getDocType() != null) {
            write("<!DOCTYPE ");
            write(getDocType());
            write(">");
            endOfLine();
        }
    }

    /**
     * Get the underlying writer
     *
     * @return the underlying writer
     */
    protected Writer getWriter() {
        return writer;
    }

    /**
     * Get the current depth in the xml indentation
     *
     * @return the current depth
     */
    protected int getDepth() {
        return depth;
    }

    /**
     * Get the current encoding in the xml
     *
     * @return the current encoding
     */
    protected String getEncoding() {
        return encoding;
    }

    /**
     * Get the docType in the xml
     *
     * @return the current docType
     */
    public String getDocType() {
        return docType;
    }

    /**
     * @return the current elementStack;
     */
    public Deque<String> getElements() {
        return elements;
    }
}
