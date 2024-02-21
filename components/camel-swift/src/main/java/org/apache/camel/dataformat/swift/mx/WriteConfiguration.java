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
package org.apache.camel.dataformat.swift.mx;

import com.prowidesoftware.swift.model.mx.EscapeHandler;
import com.prowidesoftware.swift.model.mx.MxWriteConfiguration;
import com.prowidesoftware.swift.model.mx.adapters.TypeAdaptersConfiguration;

/**
 * {@code WriteConfiguration} is a class for easy setup of a {@link MxWriteConfiguration} in a Spring application
 * configured in XML, because it provides the setters.
 */
public class WriteConfiguration extends MxWriteConfiguration {

    public String getRootElement() {
        return rootElement;
    }

    public void setRootElement(String rootElement) {
        this.rootElement = rootElement;
    }

    public boolean isIncludeXMLDeclaration() {
        return includeXMLDeclaration;
    }

    public void setIncludeXMLDeclaration(boolean includeXMLDeclaration) {
        this.includeXMLDeclaration = includeXMLDeclaration;
    }

    public EscapeHandler getEscapeHandler() {
        return escapeHandler;
    }

    public void setEscapeHandler(EscapeHandler escapeHandler) {
        this.escapeHandler = escapeHandler;
    }

    public String getHeaderPrefix() {
        return headerPrefix;
    }

    public void setHeaderPrefix(String headerPrefix) {
        this.headerPrefix = headerPrefix;
    }

    public String getDocumentPrefix() {
        return documentPrefix;
    }

    public void setDocumentPrefix(String documentPrefix) {
        this.documentPrefix = documentPrefix;
    }

    public TypeAdaptersConfiguration getAdapters() {
        return adapters;
    }

    public void setAdapters(TypeAdaptersConfiguration adapters) {
        this.adapters = adapters;
    }
}
