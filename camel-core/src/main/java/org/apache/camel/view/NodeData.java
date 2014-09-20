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
package org.apache.camel.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.camel.model.AggregateDefinition;
import org.apache.camel.model.BeanDefinition;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.FilterDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.OtherwiseDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.model.ResequenceDefinition;
import org.apache.camel.model.RoutingSlipDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.TransformDefinition;
import org.apache.camel.model.WhenDefinition;

import static org.apache.camel.util.ObjectHelper.isEmpty;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;

/**
 * Represents a node in the EIP diagram tree
 *
 * @version 
 */
@Deprecated
public class NodeData {
    public String id;
    public String image;
    public String label;
    public String shape;
    public String edgeLabel;
    public String tooltop;
    public String nodeType;
    public boolean nodeWritten;
    public String url;
    public List<ProcessorDefinition<?>> outputs;
    public String association = "property";

    public NodeData(String id, Object node, String imagePrefix) {
        this.id = id;

        if (node instanceof ProcessorDefinition) {
            ProcessorDefinition<?> processorType = (ProcessorDefinition<?>)node;
            this.edgeLabel = processorType.getLabel();
        }
        if (node instanceof FromDefinition) {
            FromDefinition fromType = (FromDefinition)node;
            this.tooltop = fromType.getLabel();
            this.label = removeQueryString(this.tooltop);
            this.url = "http://camel.apache.org/message-endpoint.html";
        } else if (node instanceof ToDefinition) {
            ToDefinition toType = (ToDefinition)node;
            this.tooltop = toType.getLabel();
            this.label = removeQueryString(this.tooltop);
            this.edgeLabel = "";
            this.url = "http://camel.apache.org/message-endpoint.html";
        } else if (node instanceof FilterDefinition) {
            this.image = imagePrefix + "MessageFilterIcon.png";
            this.label = "Filter";
            this.nodeType = "Message Filter";
        } else if (node instanceof WhenDefinition) {
            this.image = imagePrefix + "MessageFilterIcon.png";
            this.nodeType = "When Filter";
            this.label = "When";
            this.url = "http://camel.apache.org/content-based-router.html";
        } else if (node instanceof OtherwiseDefinition) {
            this.nodeType = "Otherwise";
            this.edgeLabel = "";
            this.url = "http://camel.apache.org/content-based-router.html";
            this.tooltop = "Otherwise";
        } else if (node instanceof ChoiceDefinition) {
            this.image = imagePrefix + "ContentBasedRouterIcon.png";
            this.nodeType = "Content Based Router";
            this.label = "Choice";
            this.edgeLabel = "";

            ChoiceDefinition choice = (ChoiceDefinition)node;
            List<ProcessorDefinition<?>> outputs = new ArrayList<ProcessorDefinition<?>>(choice.getWhenClauses());
            if (choice.getOtherwise() != null) {
                outputs.add(choice.getOtherwise());
            }
            this.outputs = outputs;
        } else if (node instanceof RecipientListDefinition) {
            this.image = imagePrefix + "RecipientListIcon.png";
            this.nodeType = "Recipient List";
        } else if (node instanceof RoutingSlipDefinition) {
            this.image = imagePrefix + "RoutingTableIcon.png";
            this.nodeType = "Routing Slip";
            this.url = "http://camel.apache.org/routing-slip.html";
        } else if (node instanceof SplitDefinition) {
            this.image = imagePrefix + "SplitterIcon.png";
            this.nodeType = "Splitter";
        } else if (node instanceof AggregateDefinition) {
            this.image = imagePrefix + "AggregatorIcon.png";
            this.nodeType = "Aggregator";
        } else if (node instanceof ResequenceDefinition) {
            this.image = imagePrefix + "ResequencerIcon.png";
            this.nodeType = "Resequencer";
        } else if (node instanceof BeanDefinition) {
            BeanDefinition beanRef = (BeanDefinition) node;
            this.nodeType = "Bean Ref";
            this.label = beanRef.getLabel() + " Bean"; 
            this.shape = "box";
        } else if (node instanceof TransformDefinition) {
            this.nodeType = "Transform";
            this.url = "http://camel.apache.org/message-translator.html";
        }

        // lets auto-default as many values as we can
        if (isEmpty(this.nodeType) && node != null) {
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
            if (isEmpty(this.image)) {
                this.label = this.nodeType;
                this.shape = "box";
            } else if (isNotEmpty(this.edgeLabel)) {
                this.label = "";
            } else {
                this.label = node.toString();
            }
        }
        if (isEmpty(this.tooltop)) {
            if (isNotEmpty(this.nodeType)) {
                String description = isNotEmpty(this.edgeLabel) ? this.edgeLabel : this.label;
                this.tooltop = this.nodeType + ": " + description;
            } else {
                this.tooltop = this.label;
            }
        }
        if (isEmpty(this.url) && isNotEmpty(this.nodeType)) {
            this.url = "http://camel.apache.org/" + this.nodeType.toLowerCase(Locale.ENGLISH).replace(' ', '-') + ".html";
        }
        if (node instanceof ProcessorDefinition && this.outputs == null) {
            ProcessorDefinition<?> processorType = (ProcessorDefinition<?>)node;
            this.outputs = processorType.getOutputs();
        }
    }

    protected String removeQueryString(String text) {
        int idx = text.indexOf('?');
        if (idx <= 0) {
            return text;
        } else {
            return text.substring(0, idx);
        }
    }

    /**
     * Inserts a space before each upper case letter after a lowercase
     */
    public static String insertSpacesBetweenCamelCase(String name) {
        boolean lastCharacterLowerCase = false;
        StringBuilder buffer = new StringBuilder();
        int i = 0;
        for (int size = name.length(); i < size; i++) {
            char ch = name.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (lastCharacterLowerCase) {
                    buffer.append(' ');
                }
                lastCharacterLowerCase = false;
            } else {
                lastCharacterLowerCase = true;
            }
            buffer.append(ch);
        }
        return buffer.toString();
    }
}
