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
 * Represents a CSV (Comma Separated Values) {@link org.apache.camel.spi.DataFormat}
 *
 * @version 
 */
@XmlRootElement(name = "csv")
@XmlAccessorType(XmlAccessType.FIELD)
public class CsvDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private Boolean autogenColumns;
    @XmlAttribute
    private String delimiter;
    @XmlAttribute
    private String configRef;
    @XmlAttribute
    private String strategyRef;
    @XmlAttribute
    private Boolean skipFirstLine;
    @XmlAttribute
    private Boolean lazyLoad;

    public CsvDataFormat() {
        super("csv");
    }

    public CsvDataFormat(String delimiter) {
        this();
        setDelimiter(delimiter);
    }

    public CsvDataFormat(boolean lazyLoad) {
        this();
        setLazyLoad(lazyLoad);
    }

    public Boolean isAutogenColumns() {
        return autogenColumns;
    }

    public void setAutogenColumns(Boolean autogenColumns) {
        this.autogenColumns = autogenColumns;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public String getConfigRef() {
        return configRef;
    }

    public void setConfigRef(String configRef) {
        this.configRef = configRef;
    }

    public String getStrategyRef() {
        return strategyRef;
    }

    public void setStrategyRef(String strategyRef) {
        this.strategyRef = strategyRef;
    }

    public Boolean isSkipFirstLine() {
        return autogenColumns;
    }

    public void setSkipFirstLine(Boolean skipFirstLine) {
        this.skipFirstLine = skipFirstLine;
    }

    public Boolean getLazyLoad() {
        return lazyLoad;
    }

    public void setLazyLoad(Boolean lazyLoad) {
        this.lazyLoad = lazyLoad;
    }

    @Override
    protected DataFormat createDataFormat(RouteContext routeContext) {
        DataFormat csvFormat = super.createDataFormat(routeContext);

        if (ObjectHelper.isNotEmpty(configRef)) {
            Object config = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), configRef);
            setProperty(routeContext.getCamelContext(), csvFormat, "config", config);
        }
        if (ObjectHelper.isNotEmpty(strategyRef)) {
            Object strategy = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), strategyRef);
            setProperty(routeContext.getCamelContext(), csvFormat, "strategy", strategy);
        }

        return csvFormat;
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (autogenColumns != null) {
            setProperty(camelContext, dataFormat, "autogenColumns", autogenColumns);
        }

        if (delimiter != null) {
            if (delimiter.length() > 1) {
                throw new IllegalArgumentException("Delimiter must have a length of one!");
            }
            setProperty(camelContext, dataFormat, "delimiter", delimiter);
        } else {
            // the default delimiter is ','
            setProperty(camelContext, dataFormat, "delimiter", ",");
        }

        if (skipFirstLine != null) {
            setProperty(camelContext, dataFormat, "skipFirstLine", skipFirstLine);
        }

        if (lazyLoad != null) {
            setProperty(camelContext, dataFormat, "lazyLoad", lazyLoad);
        }
    }
}
