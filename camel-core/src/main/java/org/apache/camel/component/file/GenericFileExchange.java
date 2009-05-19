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
import java.io.IOException;
import java.util.Date;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumerAware;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.impl.DefaultExchange;

public class GenericFileExchange<T> extends DefaultExchange implements PollingConsumerAware {

    private GenericFile<T> file;

    public GenericFileExchange(Endpoint fromEndpoint) {
        super(fromEndpoint);
    }

    public GenericFileExchange(DefaultExchange parent, GenericFile<T> file) {
        super(parent);
        setGenericFile(file);
    }

    protected void populateHeaders(GenericFile<T> file) {
        if (file != null) {
            getIn().setHeader(Exchange.FILE_NAME_ONLY, file.getFileNameOnly());
            getIn().setHeader(Exchange.FILE_NAME, file.getFileName());
            getIn().setHeader("CamelFileAbsolute", file.isAbsolute());
            getIn().setHeader("CamelFileAbsolutePath", file.getAbsoluteFilePath());

            if (file.isAbsolute()) {
                getIn().setHeader(Exchange.FILE_PATH, file.getAbsoluteFilePath());
            } else {
                // we must normalize path according to protocol if we build our own paths
                String path = file.normalizePathToProtocol(file.getEndpointPath() + File.separator + file.getRelativeFilePath());
                getIn().setHeader(Exchange.FILE_PATH, path);
            }

            getIn().setHeader("CamelFileRelativePath", file.getRelativeFilePath());
            getIn().setHeader(Exchange.FILE_PARENT, file.getParent());

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

    public void exchangePolled(Exchange exchange) {
        try {
            // load content into memory
            file.getBinding().loadContent(exchange, file);
        } catch (IOException e) {
            throw new RuntimeExchangeException("Cannot load content of file: " + file.getAbsoluteFilePath(), exchange, e);
        }
    }
}
