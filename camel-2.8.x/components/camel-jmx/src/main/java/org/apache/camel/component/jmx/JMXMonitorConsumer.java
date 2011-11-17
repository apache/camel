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

import java.lang.management.ManagementFactory;
import java.util.UUID;

import javax.management.NotificationFilter;
import javax.management.ObjectName;
import javax.management.monitor.CounterMonitor;
import javax.management.monitor.GaugeMonitor;
import javax.management.monitor.Monitor;
import javax.management.monitor.StringMonitor;

import org.apache.camel.Processor;

/**
 * Variant of the consumer that creates and registers a monitor bean to 
 * monitor object and attribute referenced by the endpoint. The only 
 * difference here is the act of adding and removing the notification
 * listener.
 *
 */
public class JMXMonitorConsumer extends JMXConsumer {
    
    /** name of our monitor. We keep a reference since it needs to be removed when we stop listening */
    ObjectName mMonitorObjectName;

    public JMXMonitorConsumer(JMXEndpoint aEndpoint, Processor aProcessor) {
        super(aEndpoint, aProcessor);
    }

    @Override
    protected void addNotificationListener() throws Exception {
        
        JMXEndpoint ep = (JMXEndpoint) getEndpoint();
        // create the monitor bean
        Monitor bean = null;
        if (ep.getMonitorType().equals("counter")) {
            CounterMonitor counter = new CounterMonitor();
            counter.setInitThreshold(ep.getInitThreshold());
            counter.setOffset(ep.getOffset());
            counter.setModulus(ep.getModulus());
            counter.setDifferenceMode(ep.isDifferenceMode());
            counter.setNotify(true);
            bean = counter;
        } else if (ep.getMonitorType().equals("gauge")) {
            GaugeMonitor gm = new GaugeMonitor();
            gm.setNotifyHigh(ep.isNotifyHigh());
            gm.setNotifyLow(ep.isNotifyLow());
            gm.setDifferenceMode(ep.isDifferenceMode());
            Object attr = ManagementFactory.getPlatformMBeanServer().getAttribute(ep.getJMXObjectName(), ep.getObservedAttribute());
            Double highValue = ep.getThresholdHigh();
            Double lowValue = ep.getThresholdLow();
            if (attr instanceof Byte) {
                gm.setThresholds(highValue.byteValue(), lowValue.byteValue());
            } else if  (attr instanceof Integer) {
                gm.setThresholds(highValue.intValue(), lowValue.intValue());
            } else if (attr instanceof Short) {
                gm.setThresholds(highValue.shortValue(), lowValue.shortValue());
            } else if (attr instanceof Long) {
                gm.setThresholds(highValue.longValue(), lowValue.longValue());
            } else if (attr instanceof Float) {
                gm.setThresholds(highValue.floatValue(), lowValue.floatValue());
            } else {
                gm.setThresholds(highValue, lowValue);
            }
            bean = gm;
        } else if (ep.getMonitorType().equals("string")) {
            StringMonitor sm = new StringMonitor();
            sm.setNotifyDiffer(ep.isNotifyDiffer());
            sm.setNotifyMatch(ep.isNotifyMatch());
            sm.setStringToCompare(ep.getStringToCompare());
            bean = sm;
        }
        
        bean.addObservedObject(ep.getJMXObjectName());
        bean.setObservedAttribute(ep.getObservedAttribute());
        bean.setGranularityPeriod(ep.getGranularityPeriod());

        // register the bean
        mMonitorObjectName = new ObjectName(ep.getObjectDomain(), "name", "camel-jmx-monitor-" + UUID.randomUUID());
        ManagementFactory.getPlatformMBeanServer().registerMBean(bean, mMonitorObjectName); 
        
        // add ourselves as a listener to it
        NotificationFilter nf = ep.getNotificationFilter();
        getServerConnection().addNotificationListener(mMonitorObjectName, this, nf, bean);
        bean.start();
    }

    @Override
    protected void removeNotificationListener() throws Exception {
        // remove ourselves as a listener
        ManagementFactory.getPlatformMBeanServer().removeNotificationListener(mMonitorObjectName, this);
        // unregister the monitor bean
        ManagementFactory.getPlatformMBeanServer().unregisterMBean(mMonitorObjectName);
    }

}
