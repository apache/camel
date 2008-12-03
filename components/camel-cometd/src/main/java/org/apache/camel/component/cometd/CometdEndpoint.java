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
package org.apache.camel.component.cometd;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * Endpoint for Camel Cometd.
 *
 * @version $Revision:520964 $
 */
public class CometdEndpoint extends DefaultEndpoint {
   
    private String          resourceBase;
    private int             timeout = 240000;
    private int             interval;
    private int             maxInterval = 30000;
    private int             multiFrameInterval = 1500;
    private boolean         jsonCommented = true;
    private int             logLevel = 1;
    private URI             uri;
    private CometdComponent component;
    
    @SuppressWarnings("unchecked")
    public CometdEndpoint(CometdComponent component, String uri, String remaining, Map parameters) {
        super(uri);
        this.component = component;
        try {
            this.uri = new URI(uri);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public Producer createProducer() throws Exception {
        CometdProducer producer = new CometdProducer(this);
        return producer;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        CometdConsumer consumer =  new CometdConsumer(this, processor);
        return consumer;
    }

    
    public void connect(CometdProducerConsumer prodcons) throws Exception {
        component.connect(prodcons);
    }
    
    public void disconnect(CometdProducerConsumer prodcons) throws Exception {
        component.disconnect(prodcons);
    }
    
    public CometdComponent getComponent() {
        return component;
    }

    public boolean isSingleton() {
        return false;
    }
    
    public String getPath() {
        return uri.getPath();
    }

    public int getPort() {
        if (uri.getPort() == -1) {
            if ("cometds".equals(getProtocol())) {
                return 443;
            } else {
                return 80;
            }
        }
        return uri.getPort();
    }

    public String getProtocol() {
        return uri.getScheme();
    }

    public URI getUri() {
        return uri;
    }
    
    
    public String getResourceBase() {
        return resourceBase;
    }

    public void setResourceBase(String resourceBase) {
        this.resourceBase = resourceBase;
    }
   
    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public int getMaxInterval() {
        return maxInterval;
    }

    public void setMaxInterval(int maxInterval) {
        this.maxInterval = maxInterval;
    }

    public int getMultiFrameInterval() {
        return multiFrameInterval;
    }

    public void setMultiFrameInterval(int multiFrameInterval) {
        this.multiFrameInterval = multiFrameInterval;
    }

    public boolean isJsonCommented() {
        return jsonCommented;
    }

    public void setJsonCommented(boolean commented) {
        jsonCommented = commented;
    }

    public int getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(int logLevel) {
        this.logLevel = logLevel;
    }
}
