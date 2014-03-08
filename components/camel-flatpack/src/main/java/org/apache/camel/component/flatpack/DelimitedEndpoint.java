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
package org.apache.camel.component.flatpack;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import net.sf.flatpack.Parser;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;

/**
 * @version 
 */
public class DelimitedEndpoint extends FixedLengthEndpoint {
    private char delimiter = ',';
    private char textQualifier = '"';
    private boolean ignoreFirstRecord = true;

    public DelimitedEndpoint() {
    }

    public DelimitedEndpoint(String endpointUri, Component component, String resourceUri) {
        super(endpointUri, component, resourceUri);
    }

    public Parser createParser(Exchange exchange) throws InvalidPayloadException, IOException {
        Reader bodyReader = exchange.getIn().getMandatoryBody(Reader.class);
        if (ObjectHelper.isEmpty(getDefinition())) {
            return getParserFactory().newDelimitedParser(bodyReader, delimiter, textQualifier);
        } else {
            InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext().getClassResolver(), definition);
            InputStreamReader reader = new InputStreamReader(is, IOHelper.getCharsetName(exchange));
            Parser parser = getParserFactory().newDelimitedParser(reader, bodyReader, delimiter, textQualifier, ignoreFirstRecord);
            if (isAllowShortLines()) {
                parser.setHandlingShortLines(true);
                parser.setIgnoreParseWarnings(true);
            }
            if (isIgnoreExtraColumns()) {
                parser.setIgnoreExtraColumns(true);
                parser.setIgnoreParseWarnings(true);
            }
            return parser;
        }
    }


    // Properties
    //-------------------------------------------------------------------------

    public char getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(char delimiter) {
        this.delimiter = delimiter;
    }

    public boolean isIgnoreFirstRecord() {
        return ignoreFirstRecord;
    }

    public void setIgnoreFirstRecord(boolean ignoreFirstRecord) {
        this.ignoreFirstRecord = ignoreFirstRecord;
    }

    public char getTextQualifier() {
        return textQualifier;
    }

    public void setTextQualifier(char textQualifier) {
        this.textQualifier = textQualifier;
    }

}
