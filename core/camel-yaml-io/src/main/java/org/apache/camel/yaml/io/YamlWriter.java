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
package org.apache.camel.yaml.io;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * YAML writer which emits nicely formatted documents.
 */
public class YamlWriter {

    private final Writer writer;
    private final int spaces;
    private final String lineSeparator;

    private final List<EipNode> root = new ArrayList<>();
    private final Deque<EipNode> stack = new ArrayDeque<>();

    /**
     * @param writer not null
     */
    public YamlWriter(Writer writer) throws IOException {
        this(writer, 2, null);
    }

    /**
     * @param writer        not null
     * @param spaces        number of spaces to indent
     * @param lineSeparator could be null, but the normal way is valid line separator ("\n" on UNIX).
     */
    public YamlWriter(Writer writer, int spaces, String lineSeparator) throws IOException {
        this.writer = writer;
        this.spaces = spaces;
        this.lineSeparator = validateLineSeparator(lineSeparator);
    }

    private static String validateLineSeparator(String lineSeparator) {
        String ls = lineSeparator != null ? lineSeparator : System.lineSeparator();
        if (!(ls.equals("\n") || ls.equals("\r") || ls.equals("\r\n"))) {
            throw new IllegalArgumentException("Requested line separator is invalid.");
        }
        return ls;
    }

    private EipNode getParent() {
        for (EipNode node : stack) {
            if (node.isOutput()) {
                return node;
            }
        }
        return null;
    }

    public void startElement(String name)
            throws IOException {

        //    public void startElement(String name, boolean supportOutput, boolean supportExpression, boolean language)
        //            throws IOException {
        //        EipNode parent = getParent();
        //        EipNode node = new EipNode(name, parent, supportOutput, supportExpression);

        /*
        if (parent != null && "from".equals(name)) {
            parent.setInput(node);
        } else {
            EipNode last = !stack.isEmpty() ? stack.peek() : null;
            if (last != null && language) {
                last.addExpression(node);
            } else if (last != null && last.isOutput()) {
                last.addOutput(node);
            } else {
                boolean added = addOutput(node, parent);
                if (!added) {
                    root.add(node);
                }
            }
            //        } else if (language) {
            //            addExpression(node, parent);
            //        } else {
            //            boolean added = addOutput(node, parent);
            //            if (!added) {
            //                root.add(node);
            //            }
        }
        stack.push(node);
        */
    }

    private void addExpression(EipNode node, EipNode parent) {
        EipNode last = !stack.isEmpty() ? stack.peek() : null;
        if (last != null && last.isExpression()) {
            last.addExpression(node);
        } else if (parent != null) {
            parent.addExpression(node);
            //            if (parent.isExpression()) {
            //                parent.addExpression(node);
            //            } else if (parent.isOutput()) {
            //                parent.addOutput(node);
            //            } else {
            //                addExpression(node, parent.getParent());
            //            }
        }
    }

    private boolean addOutput(EipNode node, EipNode parent) {
        if (parent == null) {
            return false;
        }
        if (parent.isOutput()) {
            parent.addOutput(node);
            return true;
        } else {
            return addOutput(node, parent.getParent());
        }
    }

    public void writeText(String text) throws IOException {
        EipNode node = stack.peek();
        if (node != null) {
            node.setText(text);
        }
    }

    public void addAttribute(String key, Object value) throws IOException {
        // skip unwanted IDs
        if ("customId".equals(key)) {
            return;
        }

        EipNode node = stack.peek();
        if (node != null) {
            node.addProperty(key, value);
        }
    }

    public void endElement() throws IOException {
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            // we are done
            writer.write(toYaml());
        }
    }

    /**
     * Write a string to the underlying writer
     */
    private void write(String str) throws IOException {
        getWriter().write(str);
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
     * Get the underlying writer
     *
     * @return the underlying writer
     */
    protected Writer getWriter() {
        return writer;
    }

    public String toYaml() {
        StringBuilder sb = new StringBuilder();
        for (EipNode node : root) {
            String s = node.dump(0, spaces, lineSeparator, true);
            sb.append(s);
            sb.append(lineSeparator);
        }
        return sb.toString();
    }

}
