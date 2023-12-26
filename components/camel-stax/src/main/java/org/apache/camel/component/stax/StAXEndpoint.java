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
package org.apache.camel.component.stax;

import org.xml.sax.ContentHandler;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Processor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.ProcessorEndpoint;

/**
 * Process XML payloads by a SAX ContentHandler.
 */
@UriEndpoint(firstVersion = "2.9.0", scheme = "stax", title = "StAX", syntax = "stax:contentHandlerClass", producerOnly = true,
             remote = false, category = { Category.TRANSFORMATION })
public class StAXEndpoint extends ProcessorEndpoint {

    @UriPath
    @Metadata(required = true)
    private String contentHandlerClass;

    public StAXEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public String getContentHandlerClass() {
        return contentHandlerClass;
    }

    /**
     * The FQN class name for the ContentHandler implementation to use.
     */
    public void setContentHandlerClass(String contentHandlerClass) {
        this.contentHandlerClass = contentHandlerClass;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        Processor target;
        if (EndpointHelper.isReferenceParameter(contentHandlerClass)) {
            ContentHandler handler = EndpointHelper.resolveReferenceParameter(getCamelContext(),
                    contentHandlerClass.substring(1), ContentHandler.class, true);
            target = new StAXProcessor(handler);
        } else {
            Class<ContentHandler> clazz
                    = getCamelContext().getClassResolver().resolveMandatoryClass(contentHandlerClass, ContentHandler.class);
            target = new StAXProcessor(clazz);
        }

        setProcessor(target);
    }

}
