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
package org.apache.camel.component.jhc;

import java.net.URI;

import org.apache.camel.Consumer;
import org.apache.camel.HeaderFilterStrategyAware;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

/**
 * Created by IntelliJ IDEA.
 * User: gnodet
 * Date: Sep 7, 2007
 * Time: 8:06:42 PM
 * To change this template use File | Settings | File Templates.
 */
public class JhcEndpoint extends DefaultEndpoint<JhcExchange> {

    private HttpParams params;
    private URI httpUri;

    public JhcEndpoint(String endpointUri, JhcComponent component, URI httpUri) {
        super(endpointUri, component);
        params = component.getParams().copy();
        this.httpUri = httpUri;
    }

    public JhcEndpoint(String endpointUri, URI httpUri, HttpParams params) {
        super(endpointUri);
        this.httpUri = httpUri;
        this.params = params;
    }

    public HttpParams getParams() {
        return params;
    }

    public void setParams(HttpParams params) {
        this.params = params;
    }

    public URI getHttpUri() {
        return httpUri;
    }

    public void setHttpUri(URI httpUri) {
        this.httpUri = httpUri;
    }

    public String getProtocol() {
        return httpUri.getScheme();
    }

    public String getHost() {
        return httpUri.getHost();
    }

    public int getPort() {
        if (httpUri.getPort() == -1) {
            if ("https".equals(getProtocol())) {
                return 443;
            } else {
                return 80;
            }
        }
        return httpUri.getPort();
    }

    public String getPath() {
        return httpUri.getPath();
    }

    public boolean isSingleton() {
        return true;
    }

    public Producer<JhcExchange> createProducer() throws Exception {
        return new JhcProducer(this);
    }

    public Consumer<JhcExchange> createConsumer(Processor processor) throws Exception {
        return new JhcConsumer(this, processor);
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        if (getComponent() instanceof HeaderFilterStrategyAware) {
            return ((HeaderFilterStrategyAware)getComponent()).getHeaderFilterStrategy();
        } else {
            return new JhcHeaderFilterStrategy();
        }
    }
}
