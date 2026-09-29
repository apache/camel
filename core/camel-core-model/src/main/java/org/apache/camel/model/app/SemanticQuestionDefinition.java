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
package org.apache.camel.model.app;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.spi.Metadata;

/** A semantic question with the same fields and policies as a YAML declaration. */
@Metadata(label = "configuration")
@XmlType(name = "semanticQuestionDefinition", propOrder = { "instructions", "criteria", "levels" })
@XmlAccessorType(XmlAccessType.FIELD)
public class SemanticQuestionDefinition {
    @XmlAttribute(required = true)
    @Metadata(required = true, description = "The context-wide question name.")
    private String name;
    @XmlAttribute(required = true)
    @Metadata(required = true, enums = "boolean,choice,score", description = "The question type.")
    private String type;
    @XmlAttribute
    @Metadata(description = "The Simple expression selecting the message state.")
    private String state;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Double", defaultValue = "0.5", description = "The boolean decision threshold.")
    private String threshold;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Double", defaultValue = "0", description = "The boolean uncertainty band.")
    private String uncertainty;
    @XmlAttribute
    @Metadata(defaultValue = "fail", enums = "fail,non-match", description = "The boolean uncertainty policy.")
    private String uncertaintyPolicy;
    @XmlElement(required = true)
    @Metadata(required = true, description = "Instructions describing the judgment to make.")
    private String instructions;
    @XmlElement(name = "criterion")
    @Metadata(description = "Named choice criteria, or optional true/false boolean criteria.")
    private List<PropertyDefinition> criteria = new ArrayList<>();
    @XmlElement(name = "level")
    @Metadata(description = "Ordered descriptive levels for a score question.")
    private List<String> levels = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getThreshold() {
        return threshold;
    }

    public void setThreshold(String threshold) {
        this.threshold = threshold;
    }

    public String getUncertainty() {
        return uncertainty;
    }

    public void setUncertainty(String uncertainty) {
        this.uncertainty = uncertainty;
    }

    public String getUncertaintyPolicy() {
        return uncertaintyPolicy;
    }

    public void setUncertaintyPolicy(String uncertaintyPolicy) {
        this.uncertaintyPolicy = uncertaintyPolicy;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public List<PropertyDefinition> getCriteria() {
        return criteria;
    }

    public void setCriteria(List<PropertyDefinition> criteria) {
        this.criteria = criteria;
    }

    public List<String> getLevels() {
        return levels;
    }

    public void setLevels(List<String> levels) {
        this.levels = levels;
    }

    /** The question type. */
    public SemanticQuestionDefinition type(String type) {
        this.type = type;
        return this;
    }

    /** The Simple expression selecting the message state. */
    public SemanticQuestionDefinition state(String state) {
        this.state = state;
        return this;
    }

    /** The boolean decision threshold. */
    public SemanticQuestionDefinition threshold(double threshold) {
        return threshold(Double.toString(threshold));
    }

    /** The boolean decision threshold, allowing property placeholders. */
    public SemanticQuestionDefinition threshold(String threshold) {
        this.threshold = threshold;
        return this;
    }

    /** The boolean uncertainty band. */
    public SemanticQuestionDefinition uncertainty(double uncertainty) {
        return uncertainty(Double.toString(uncertainty));
    }

    /** The boolean uncertainty band, allowing property placeholders. */
    public SemanticQuestionDefinition uncertainty(String uncertainty) {
        this.uncertainty = uncertainty;
        return this;
    }

    /** The boolean uncertainty policy. */
    public SemanticQuestionDefinition uncertaintyPolicy(String uncertaintyPolicy) {
        this.uncertaintyPolicy = uncertaintyPolicy;
        return this;
    }

    /** Instructions describing the judgment to make. */
    public SemanticQuestionDefinition instructions(String instructions) {
        this.instructions = instructions;
        return this;
    }

    /** Add a named criterion. */
    public SemanticQuestionDefinition criterion(String name, String description) {
        criteria.add(new PropertyDefinition(name, description));
        return this;
    }

    /** Add the next descriptive score level. */
    public SemanticQuestionDefinition level(String description) {
        levels.add(description);
        return this;
    }
}
