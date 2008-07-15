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
package org.apache.camel.component.flatpack;

import net.sf.flatpack.DefaultParserFactory;
import net.sf.flatpack.Parser;
import net.sf.flatpack.ParserFactory;
import net.sf.flatpack.DataSet;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ObjectHelper;
import org.springframework.core.io.Resource;

import java.io.*;

/**
 * @version $Revision: 1.1 $
 */
public class FlatpackDataFormat implements DataFormat {
    private char delimiter = ',';
    private char textQualifier = '"';
    private boolean ignoreFirstRecord = true;
    private Resource definition;
    private boolean fixed = false;
    private ParserFactory parserFactory = DefaultParserFactory.getInstance();

    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        // TODO
        throw new UnsupportedOperationException("marshal() not implemented yet!");
    }

    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        // TODO should we just grab a Reader from the body?
        InputStreamReader reader = new InputStreamReader(stream);
        Parser parser = createParser(exchange, reader);
        DataSet dataSet = parser.parse();
        return new DataSetList(dataSet);
    }

    // Properties
    //-------------------------------------------------------------------------

    public boolean isFixed() {
        return fixed;
    }

    public void setFixed(boolean fixed) {
        this.fixed = fixed;
    }

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


    public Resource getDefinition() {
        return definition;
    }

    public void setDefinition(Resource definition) {
        this.definition = definition;
    }

    public ParserFactory getParserFactory() {
        return parserFactory;
    }

    public void setParserFactory(ParserFactory parserFactory) {
        this.parserFactory = parserFactory;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected Parser createParser(Exchange exchange, Reader bodyReader) throws IOException {
        if (isFixed()) {
            Resource resource = getDefinition();
            ObjectHelper.notNull(resource, "resource property");
            return getParserFactory().newFixedLengthParser(new InputStreamReader(resource.getInputStream()), bodyReader);
        } else {
            Resource resource = getDefinition();
            if (resource == null) {
                return getParserFactory().newDelimitedParser(bodyReader, delimiter, textQualifier);
            } else {
                return getParserFactory().newDelimitedParser(new InputStreamReader(resource.getInputStream()), bodyReader, delimiter, textQualifier, ignoreFirstRecord);
            }
        }
    }

}
