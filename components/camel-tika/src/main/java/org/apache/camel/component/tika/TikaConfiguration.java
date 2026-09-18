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
package org.apache.camel.component.tika;

import java.nio.charset.Charset;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.tika.config.loader.TikaLoader;

@UriParams
public class TikaConfiguration {

    @UriPath(description = "Operation type")
    @Metadata(required = true)
    private TikaOperation operation;
    @UriParam(defaultValue = "xml")
    private TikaParseOutputFormat tikaParseOutputFormat = TikaParseOutputFormat.xml;
    @UriParam(description = "Tika Parse Output Encoding")
    private String tikaParseOutputEncoding = Charset.defaultCharset().name();
    @UriParam(label = "advanced", description = "Tika loader used to configure parsers and detectors")
    private TikaLoader tikaLoader;
    @UriParam(label = "advanced", description = "Path to a Tika JSON configuration file")
    private String tikaConfigFile;

    public TikaOperation getOperation() {
        return operation;
    }

    /**
     * Tika Operation - parse or detect
     */
    public void setOperation(TikaOperation operation) {
        this.operation = operation;
    }

    public void setOperation(String operation) {
        this.operation = TikaOperation.valueOf(operation);
    }

    public TikaParseOutputFormat getTikaParseOutputFormat() {
        return tikaParseOutputFormat;
    }

    /**
     * Tika Output Format. Supported output formats.
     * <ul>
     * <li>xml: Returns Parsed Content as XML.</li>
     * <li>html: Returns Parsed Content as HTML.</li>
     * <li>text: Returns Parsed Content as Text.</li>
     * <li>textMain: Uses the <a href="http://code.google.com/p/boilerpipe/">boilerpipe</a> library to automatically
     * extract the main content from a web page.</li>
     * </ul>
     */
    public void setTikaParseOutputFormat(TikaParseOutputFormat tikaParseOutputFormat) {
        this.tikaParseOutputFormat = tikaParseOutputFormat;
    }

    public String getTikaParseOutputEncoding() {
        return tikaParseOutputEncoding;
    }

    /**
     * Tika Parse Output Encoding - Used to specify the character encoding of the parsed output. Defaults to
     * Charset.defaultCharset().
     */
    public void setTikaParseOutputEncoding(String tikaParseOutputEncoding) {
        this.tikaParseOutputEncoding = tikaParseOutputEncoding;
    }

    public TikaLoader getTikaLoader() {
        return tikaLoader;
    }

    /**
     * To use a custom Tika loader.
     */
    public void setTikaLoader(TikaLoader tikaLoader) {
        this.tikaLoader = tikaLoader;
    }

    public String getTikaConfigFile() {
        return tikaConfigFile;
    }

    /**
     * Tika configuration file: The path of the Tika JSON configuration file to use.
     */
    public void setTikaConfigFile(String tikaConfigFile) {
        this.tikaConfigFile = tikaConfigFile;
    }
}
