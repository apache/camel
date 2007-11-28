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
package org.apache.camel.component.xslt;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.xml.XsltBuilder;
import org.apache.camel.component.ResourceBasedComponent;
import org.apache.camel.impl.ProcessorEndpoint;
import org.springframework.core.io.Resource;

/**
 * An <a href="http://activemq.apache.org/camel/xslt.html">XSLT Component</a>
 * for performing XSLT transforms of messages
 *
 * @version $Revision: 1.1 $
 */
public class XsltComponent extends ResourceBasedComponent {

    protected Endpoint<Exchange> createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        Resource resource = resolveMandatoryResource(remaining);
        if (LOG.isDebugEnabled()) {
            LOG.debug(this + " using schema resource: " + resource);
        }
        XsltBuilder xslt = newInstance(XsltBuilder.class);
        xslt.setTransformerInputStream(resource.getInputStream());
        configureXslt(xslt, uri, remaining, parameters);
        return new ProcessorEndpoint(uri, this, xslt);
    }

    protected void configureXslt(XsltBuilder xslt, String uri, String remaining, Map parameters) throws Exception {
        setProperties(xslt, parameters);
    }
}
