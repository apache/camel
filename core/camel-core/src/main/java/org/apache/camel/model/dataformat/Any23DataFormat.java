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
package org.apache.camel.model.dataformat;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Any23 data format is used for parsing data to RDF.
 */
@Metadata(firstVersion = "1.0.0", label = "dataformat,transformation", title = "Any23")
@XmlRootElement(name = "any23")
@XmlAccessorType(XmlAccessType.FIELD)
public class Any23DataFormat extends DataFormatDefinition {

  @XmlAttribute
  @Metadata(defaultValue = "MODEL")
  private String outputFormat;
  @XmlAttribute
  private String configurations;
  @XmlAttribute
  private String extractors;
  @XmlAttribute
  private String baseuri;

  public Any23DataFormat() {
    super("any23");
  }

  public Any23DataFormat(String baseuri) {
    this();
    this.baseuri = baseuri;
  }

  public Any23DataFormat(String baseuri, String outputFormat) {
    this(baseuri);
    this.outputFormat = outputFormat;
  }

  public Any23DataFormat(String baseuri, String outputFormat, String configurations) {
    this(baseuri, outputFormat);
    this.outputFormat = outputFormat;
    this.configurations = configurations;
  }

  public Any23DataFormat(String baseuri, String outputFormat, String configurations, String extractors) {
    this(baseuri, outputFormat, configurations);
    this.outputFormat = outputFormat;
    this.configurations = configurations;
    this.extractors = extractors;
  }

  public String getOutputFormat() {
    return outputFormat;
  }

  public void setOutputFormat(String outputFormat) {
    this.outputFormat = outputFormat;
  }

  public String getConfigurations() {
    return configurations;
  }

  public void setConfigurations(String configurations) {
    this.configurations = configurations;
  }

  public String getExtractors() {
    return extractors;
  }

  public void setExtractors(String extractors) {
    this.extractors = extractors;
  }

  public String getBaseuri() {
    return baseuri;
  }

  public void setBaseuri(String baseuri) {
    this.baseuri = baseuri;
  }

}
