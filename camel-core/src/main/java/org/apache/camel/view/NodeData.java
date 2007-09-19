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
package org.apache.camel.view;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.model.*;
import static org.apache.camel.util.ObjectHelper.isNotNullAndNonEmpty;
import static org.apache.camel.util.ObjectHelper.isNullOrBlank;

/**
 * Represents a node in the EIP diagram tree
 *
 * @version $Revision: 1.1 $
 */
public class NodeData {
    public String id;
    private final String imagePrefix;
    public String image;
    public String label;
    public String shape;
    public String edgeLabel;
    public String tooltop;
    public String nodeType;
    public boolean nodeWritten;
    public String url;
    public List<ProcessorType> outputs;
    public String association = "property";

    public NodeData(String id, Object node, String imagePrefix) {
        this.id = id;
        this.imagePrefix = imagePrefix;

        if (node instanceof ProcessorType) {
            ProcessorType processorType = (ProcessorType) node;
            this.edgeLabel = processorType.getLabel();
        }
        if (node instanceof FromType) {
            FromType fromType = (FromType) node;
            this.tooltop = fromType.getLabel();
            this.label = removeQueryString(this.tooltop);
            this.url = "http://activemq.apache.org/camel/message-endpoint.html";
        }
        else if (node instanceof ToType) {
            ToType toType = (ToType) node;
            this.tooltop = toType.getLabel();
            this.label = removeQueryString(this.tooltop);
            this.edgeLabel = "";
            this.url = "http://activemq.apache.org/camel/message-endpoint.html";
        }
        else if (node instanceof FilterType) {
            this.image = imagePrefix + "MessageFilterIcon.gif";
            this.nodeType = "Message Filter";
        }
        else if (node instanceof WhenType) {
            this.image = imagePrefix + "MessageFilterIcon.gif";
            this.nodeType = "When Filter";
            this.url = "http://activemq.apache.org/camel/content-based-router.html";
        }
        else if (node instanceof OtherwiseType) {
            this.nodeType = "Otherwise";
            this.edgeLabel = "";
            this.url = "http://activemq.apache.org/camel/content-based-router.html";
            this.tooltop = "Otherwise";
        }
        else if (node instanceof ChoiceType) {
            this.image = imagePrefix + "ContentBasedRouterIcon.gif";
            this.nodeType = "Content Based Router";
            this.label = "";
            this.edgeLabel = "";

            ChoiceType choice = (ChoiceType) node;
            List<ProcessorType> outputs = new ArrayList<ProcessorType>(choice.getWhenClauses());
            outputs.add(choice.getOtherwise());
            this.outputs = outputs;
        }
        else if (node instanceof RecipientListType) {
            this.image = imagePrefix + "RecipientListIcon.gif";
            this.nodeType = "Recipient List";
        }
        else if (node instanceof SplitterType) {
            this.image = imagePrefix + "SplitterIcon.gif";
            this.nodeType = "Splitter";
        }
        else if (node instanceof AggregatorType) {
            this.image = imagePrefix + "AggregatorIcon.gif";
            this.nodeType = "Aggregator";
        }
        else if (node instanceof ResequencerType) {
            this.image = imagePrefix + "ResequencerIcon.gif";
            this.nodeType = "Resequencer";
        }

        // lets auto-default as many values as we can
        if (isNullOrBlank(this.nodeType)) {
            // TODO we could add this to the model?
            String name = node.getClass().getName();
            int idx = name.lastIndexOf('.');
            if (idx > 0) {
                name = name.substring(idx + 1);
            }
            if (name.endsWith("Type")) {
                name = name.substring(0, name.length() - 4);
            }
            this.nodeType = insertSpacesBetweenCamelCase(name);
        }
        if (this.label == null) {
            if (isNullOrBlank(this.image)) {
                this.label = this.nodeType;
                this.shape = "box";
            }
            else if (isNotNullAndNonEmpty(this.edgeLabel)) {
                this.label = "";
            }
            else {
                this.label = node.toString();
            }
        }
        if (isNullOrBlank(this.tooltop)) {
            if (isNotNullAndNonEmpty(this.nodeType)) {
                String description = isNotNullAndNonEmpty(this.edgeLabel) ? this.edgeLabel : this.label;
                this.tooltop = this.nodeType + ": " + description;
            }
            else {
                this.tooltop = this.label;
            }
        }
        if (isNullOrBlank(this.url) && isNotNullAndNonEmpty(this.nodeType)) {
            this.url = "http://activemq.apache.org/camel/" + this.nodeType.toLowerCase().replace(' ', '-') + ".html";
        }
        if (node instanceof ProcessorType && this.outputs == null) {
            ProcessorType processorType = (ProcessorType) node;
            this.outputs = processorType.getOutputs();
        }
    }

    protected String removeQueryString(String text) {
        int idx = text.indexOf("?");
        if (idx <= 0) {
            return text;
        }
        else {
            return text.substring(0, idx);
        }
    }

    /**
     * lets insert a space before each upper case letter after a lowercase
     *
     * @param name
     * @return
     */
    public static String insertSpacesBetweenCamelCase(String name) {
        boolean lastCharacterLowerCase = false;
        StringBuffer buffer = new StringBuffer();
        for (int i = 0, size = name.length(); i < size; i++) {
            char ch = name.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (lastCharacterLowerCase) {
                    buffer.append(' ');
                }
                lastCharacterLowerCase = false;
            }
            else {
                lastCharacterLowerCase = true;
            }
            buffer.append(ch);
        }
        return buffer.toString();
    }
}
