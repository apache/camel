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
package org.apache.camel.component.jmx;

import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.ObjectName;
import javax.management.monitor.CounterMonitor;

import org.apache.camel.Consumer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * JMXEndpoint for monitoring JMX attributs using {@link CounterMonitor}.
 *
 * @version $Revision$
 */
public class JMXEndpoint extends DefaultEndpoint<JMXExchange> {
    private static final transient Log LOG = LogFactory.getLog(JMXEndpoint.class);
    private String name;
    private ObjectName ourName;
    private String observedObjectName;
    private String attributeName;
    private long granularityPeriod = 5000;
    private Number threshold;
    private Number offset;
    private MBeanServer mbeanServer;
    private CounterMonitor counterMonitor = new CounterMonitor();

    protected JMXEndpoint(String endpointUri, JMXComponent component) {
        super(endpointUri, component);
        observedObjectName = endpointUri;
    }

    public JMXEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public Producer<JMXExchange> createProducer() throws Exception {
        throw new UnsupportedOperationException("Producer not supported");
    }

    public Consumer<JMXExchange> createConsumer(Processor proc) throws Exception {
        ObjectName observedName = new ObjectName(observedObjectName);
        if (name == null) {
            String type = observedName.getKeyProperty("type");
            type = type != null ? type : "UNKNOWN";
            name = mbeanServer.getDefaultDomain() + ":type=CounterMonitor_" + type;
        }

        JMXConsumer result = new JMXConsumer(this, proc);
        ourName = new ObjectName(name);
        counterMonitor.setNotify(true);
        counterMonitor.addObservedObject(observedName);
        counterMonitor.setObservedAttribute(attributeName);
        counterMonitor.setGranularityPeriod(granularityPeriod);
        counterMonitor.setDifferenceMode(false);
        counterMonitor.setInitThreshold(threshold);
        counterMonitor.setOffset(offset);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Registering and adding notification listener for [" + counterMonitor + "] with name [" + ourName + "]");
        }
        mbeanServer.registerMBean(counterMonitor, ourName);
        // TODO: How do we remove the listener?
        mbeanServer.addNotificationListener(ourName, result, null, new Object());
        return result;
    }

    public boolean isSingleton() {
        return true;
    }

    public JMXExchange createExchange(Notification notification) {
        return new JMXExchange(getCamelContext(), getExchangePattern(), notification);
    }

    public JMXExchange createExchange() {
        return new JMXExchange(getCamelContext(), getExchangePattern(), null);
    }

    public JMXExchange createExchange(ExchangePattern pattern) {
        return new JMXExchange(getCamelContext(), pattern, null);
    }

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public long getGranularityPeriod() {
        return granularityPeriod;
    }

    public void setGranularityPeriod(long granularityPeriod) {
        this.granularityPeriod = granularityPeriod;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Number getOffset() {
        return offset;
    }

    public void setOffset(Number offset) {
        this.offset = offset;
    }

    public Number getThreshold() {
        return threshold;
    }

    public void setThreshold(Number threshold) {
        this.threshold = threshold;
    }

    public MBeanServer getMbeanServer() {
        return mbeanServer;
    }

    public void setMbeanServer(MBeanServer mbeanServer) {
        this.mbeanServer = mbeanServer;
    }
}
