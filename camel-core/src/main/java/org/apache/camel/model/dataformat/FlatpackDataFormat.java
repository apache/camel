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
package org.apache.camel.model.dataformat;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents a <a href="http://camel.apache.org/flatpack.html">Flatpack</a> {@link org.apache.camel.spi.DataFormat}.
 */
@XmlRootElement(name = "flatpack")
@XmlAccessorType(XmlAccessType.FIELD)
public class FlatpackDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private String parserFactoryRef;
    @XmlAttribute
    private String definition;
    @XmlAttribute
    private Boolean fixed;
    @XmlAttribute
    private Boolean ignoreFirstRecord;
    @XmlAttribute
    private String textQualifier;
    @XmlAttribute
    private String delimiter;
    @XmlAttribute
    private Boolean allowShortLines;
    @XmlAttribute
    private Boolean ignoreExtraColumns;

    public FlatpackDataFormat() {
        super("flatpack");
    }

    public String getParserFactoryRef() {
        return parserFactoryRef;
    }

    public void setParserFactoryRef(String parserFactoryRef) {
        this.parserFactoryRef = parserFactoryRef;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public Boolean getFixed() {
        return fixed;
    }

    public void setFixed(Boolean fixed) {
        this.fixed = fixed;
    }

    public Boolean getIgnoreFirstRecord() {
        return ignoreFirstRecord;
    }

    public void setIgnoreFirstRecord(Boolean ignoreFirstRecord) {
        this.ignoreFirstRecord = ignoreFirstRecord;
    }

    public String getTextQualifier() {
        return textQualifier;
    }

    public void setTextQualifier(String textQualifier) {
        this.textQualifier = textQualifier;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public Boolean getAllowShortLines() {
        return allowShortLines;
    }

    public void setAllowShortLines(Boolean allowShortLines) {
        this.allowShortLines = allowShortLines;
    }

    public Boolean getIgnoreExtraColumns() {
        return ignoreExtraColumns;
    }

    public void setIgnoreExtraColumns(Boolean ignoreExtraColumns) {
        this.ignoreExtraColumns = ignoreExtraColumns;
    }

    @Override
    protected DataFormat createDataFormat(RouteContext routeContext) {
        DataFormat flatpack = super.createDataFormat(routeContext);

        if (ObjectHelper.isNotEmpty(parserFactoryRef)) {
            Object parserFactory = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), parserFactoryRef);
            setProperty(routeContext.getCamelContext(), flatpack, "parserFactory", parserFactory);
        }

        return flatpack;
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (ObjectHelper.isNotEmpty(definition)) {
            setProperty(camelContext, dataFormat, "definition", definition);
        }
        if (fixed != null) {
            setProperty(camelContext, dataFormat, "fixed", fixed);
        }
        if (ignoreFirstRecord != null) {
            setProperty(camelContext, dataFormat, "ignoreFirstRecord", ignoreFirstRecord);
        }
        if (ObjectHelper.isNotEmpty(textQualifier)) {
            if (textQualifier.length() > 1) {
                throw new IllegalArgumentException("Text qualifier must be one character long!");
            }
            setProperty(camelContext, dataFormat, "textQualifier", textQualifier.charAt(0));
        }
        if (ObjectHelper.isNotEmpty(delimiter)) {
            if (delimiter.length() > 1) {
                throw new IllegalArgumentException("Delimiter must be one character long!");
            }
            setProperty(camelContext, dataFormat, "delimiter", delimiter.charAt(0));
        }
        if (allowShortLines != null) {
            setProperty(camelContext, dataFormat, "allowShortLines", allowShortLines);
        }
        if (ignoreExtraColumns != null) {
            setProperty(camelContext, dataFormat, "ignoreExtraColumns", ignoreExtraColumns);
        }
    }
}