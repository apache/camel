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
package org.apache.camel.component.file;

import java.util.Date;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultExchange;

public class GenericFileExchange<T> extends DefaultExchange {

    private GenericFile<T> genericFile;

    public GenericFileExchange(CamelContext context) {
        super(context);
    }

    public GenericFileExchange(CamelContext context, ExchangePattern pattern) {
        super(context, pattern);
    }

    public GenericFileExchange(Exchange parent) {
        super(parent);
    }

    public GenericFileExchange(Endpoint fromEndpoint) {
        super(fromEndpoint);
    }

    public GenericFileExchange(GenericFileEndpoint endpoint, ExchangePattern pattern, GenericFile<T> genericFile) {
        super(endpoint, pattern);
        setGenericFile(genericFile);
    }

    public GenericFileExchange(DefaultExchange parent, GenericFile<T> genericFile) {
        super(parent);
        setGenericFile(genericFile);
    }

    public GenericFileExchange(Endpoint fromEndpoint, ExchangePattern pattern) {
        super(fromEndpoint, pattern);
    }

    protected void populateHeaders(GenericFile<T> genericFile) {
        if (genericFile != null) {
            getIn().setHeader("CamelFileName", genericFile.getFileName());
            getIn().setHeader("CamelFileAbsolutePath", genericFile.getAbsoluteFileName());
            // set the parent if there is a parent folder
            if (genericFile.getRelativeFileName().lastIndexOf("/") != -1) {
                String parent = genericFile.getRelativeFileName().substring(0, genericFile.getRelativeFileName().lastIndexOf("/"));
                getIn().setHeader("CamelFileParent", parent);
            }
            getIn().setHeader("CamelFilePath", genericFile.getRelativeFileName());
            getIn().setHeader("CamelFileCanonicalPath", genericFile.getCanonicalFileName());

            if (genericFile.getFileLength() > 0) {
                getIn().setHeader("CamelFileLength", genericFile.getFileLength());
            }
            if (genericFile.getLastModified() > 0) {
                getIn().setHeader("CamelFileLastModified", new Date(genericFile.getLastModified()));
            }
        }
    }

    public GenericFile<T> getGenericFile() {
        return genericFile;
    }

    public void setGenericFile(GenericFile<T> genericFile) {
        setIn(new GenericFileMessage(genericFile));
        this.genericFile = genericFile;
        populateHeaders(genericFile);
    }

    public Exchange newInstance() {
        return new GenericFileExchange<T>(this, genericFile);
    }

}
