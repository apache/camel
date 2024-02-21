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
package org.apache.camel.oaipmh.model;

import java.util.Optional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OAIPMHResponse {

    private static final Logger LOG = LoggerFactory.getLogger(OAIPMHResponse.class);
    private String rawResponse;
    private Document xmlResponse;

    public OAIPMHResponse(String rawResponse) {
        this.rawResponse = rawResponse;
        parse();
    }

    private void parse() {
        this.xmlResponse = Jsoup.parse(rawResponse, "", Parser.xmlParser());
    }

    public Optional<String> getResumptionToken() {
        Optional<String> vl = Optional.empty();
        Elements elementsByTag = xmlResponse.getElementsByTag("resumptionToken");
        if (!elementsByTag.isEmpty()) {
            if (elementsByTag.size() > 1) {
                LOG.warn("Multiple 'resumptionToken' tags detected, taking the first one.");
            }
            Element get = elementsByTag.get(0);
            vl = Optional.of(get.text().trim());
        }
        return vl;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public Document getXmlResponse() {
        return xmlResponse;
    }

    public void setXmlResponse(Document xmlResponse) {
        this.xmlResponse = xmlResponse;
    }

}
