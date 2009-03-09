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

import java.io.File;
import java.util.Date;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultExchange;

public class GenericFileExchange<T> extends DefaultExchange {

    private GenericFile<T> file;

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

    public GenericFileExchange(GenericFileEndpoint endpoint, ExchangePattern pattern, GenericFile<T> file) {
        super(endpoint, pattern);
        setGenericFile(file);
    }

    public GenericFileExchange(DefaultExchange parent, GenericFile<T> file) {
        super(parent);
        setGenericFile(file);
    }

    public GenericFileExchange(Endpoint fromEndpoint, ExchangePattern pattern) {
        super(fromEndpoint, pattern);
    }

    protected void populateHeaders(GenericFile<T> file) {
        if (file != null) {
            getIn().setHeader(Exchange.FILE_NAME_ONLY, file.getFileNameOnly());
            getIn().setHeader(Exchange.FILE_NAME, file.getFileName());
            getIn().setHeader("CamelFileAbsolute", file.isAbsolute());
            getIn().setHeader("CamelFileAbsolutePath", file.getAbsoluteFilePath());

            if (file.isAbsolute()) {
                getIn().setHeader("CamelFilePath", file.getAbsoluteFilePath());
            } else {
                // we must normal path according to protocol if we build our own paths
                String path = file.normalizePathToProtocol(file.getEndpointPath() + File.separator + file.getRelativeFilePath());
                getIn().setHeader("CamelFilePath", path);
            }

            getIn().setHeader("CamelFileRelativePath", file.getRelativeFilePath());
            getIn().setHeader("CamelFileParent", file.getParent());

            if (file.getFileLength() > 0) {
                getIn().setHeader("CamelFileLength", file.getFileLength());
            }
            if (file.getLastModified() > 0) {
                getIn().setHeader("CamelFileLastModified", new Date(file.getLastModified()));
            }
        }
    }

    public GenericFile<T> getGenericFile() {
        return file;
    }

    public void setGenericFile(GenericFile<T> file) {
        this.file = file;
        setIn(new GenericFileMessage<T>(file));
        populateHeaders(file);
    }

    public Exchange newInstance() {
        return new GenericFileExchange<T>(this, file);
    }

}
