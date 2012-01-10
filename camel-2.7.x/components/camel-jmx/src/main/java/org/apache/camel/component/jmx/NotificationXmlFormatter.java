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

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.management.AttributeChangeNotification;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.ObjectName;
import javax.management.monitor.MonitorNotification;
import javax.management.relation.RelationNotification;
import javax.management.remote.JMXConnectionNotification;
import javax.management.timer.TimerNotification;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import org.apache.camel.component.jmx.jaxb.NotificationEventType;
import org.apache.camel.component.jmx.jaxb.ObjectFactory;
import org.apache.camel.component.jmx.jaxb.ObjectNamesType;

/**
 * Converts the Notification into an XML stream.
 *
 * @author markford
 */
public class NotificationXmlFormatter {

    private DatatypeFactory mDatatypeFactory;
    private Marshaller mMarshaller;
    private Lock mMarshallerLock = new ReentrantLock(false);
    private ObjectFactory mObjectFactory = new ObjectFactory();

    public String format(Notification aNotification) throws NotificationFormatException {

        NotificationEventType jaxb = null;

        boolean wrap = false;

        if (aNotification instanceof AttributeChangeNotification) {
            AttributeChangeNotification ac = (AttributeChangeNotification) aNotification;

            jaxb = mObjectFactory.createAttributeChangeNotification()
                    .withAttributeName(ac.getAttributeName())
                    .withAttributeType(ac.getAttributeType())
                    .withNewValue(ac.getNewValue() == null ? null : String.valueOf(ac.getNewValue()))
                    .withOldValue(ac.getOldValue() == null ? null : String.valueOf(ac.getOldValue()));
        } else if (aNotification instanceof JMXConnectionNotification) {
            jaxb = mObjectFactory.createJMXConnectionNotification()
                    .withConnectionId(((JMXConnectionNotification) aNotification).getConnectionId());
        } else if (aNotification instanceof MBeanServerNotification) {
            jaxb = mObjectFactory.createMBeanServerNotification()
                    .withMBeanName(String.valueOf(((MBeanServerNotification) aNotification).getMBeanName()));
        } else if (aNotification instanceof MonitorNotification) {
            MonitorNotification mn = (MonitorNotification) aNotification;
            jaxb = mObjectFactory.createMonitorNotification()
                    .withDerivedGauge(String.valueOf(mn.getDerivedGauge()))
                    .withObservedAttribute(mn.getObservedAttribute())
                    .withObservedObject(String.valueOf(mn.getObservedObject()))
                    .withTrigger(String.valueOf(mn.getTrigger()));
        } else if (aNotification instanceof RelationNotification) {
            RelationNotification rn = (RelationNotification) aNotification;
            jaxb = mObjectFactory.createRelationNotification()
                    .withObjectName(String.valueOf(rn.getObjectName()))
                    .withRelationId(rn.getRelationId())
                    .withRelationTypeName(rn.getRelationTypeName())
                    .withRoleName(rn.getRoleName());
            if (rn.getNewRoleValue() != null) {
                ObjectNamesType ont = toObjectNamesType(rn.getNewRoleValue());
                ((org.apache.camel.component.jmx.jaxb.RelationNotification) jaxb).withNewRoleValue(ont);
            }
            if (rn.getOldRoleValue() != null) {
                ObjectNamesType ont = toObjectNamesType(rn.getOldRoleValue());
                ((org.apache.camel.component.jmx.jaxb.RelationNotification) jaxb).withOldRoleValue(ont);
            }
            if (rn.getMBeansToUnregister() != null) {
                ObjectNamesType ont = toObjectNamesType(rn.getMBeansToUnregister());
                ((org.apache.camel.component.jmx.jaxb.RelationNotification) jaxb).withMBeansToUnregister(ont);
            }
        } else if (aNotification instanceof TimerNotification) {
            jaxb = mObjectFactory.createTimerNotification().withNotificationId(((TimerNotification) aNotification).getNotificationID());
        } else {
            jaxb = mObjectFactory.createNotificationEventType();
            wrap = true;
        }

        // add all of the common properties
        jaxb.withMessage(aNotification.getMessage())
                .withSequence(aNotification.getSequenceNumber())
                .withSource(String.valueOf(aNotification.getSource()))
                .withTimestamp(aNotification.getTimeStamp())
                .withType(aNotification.getType());
        if (aNotification.getUserData() != null) {
            jaxb.withUserData(String.valueOf(aNotification.getUserData()));
        }

        try {
            DatatypeFactory df = getDatatypeFactory();
            Date date = new Date(aNotification.getTimeStamp());
            GregorianCalendar gc = new GregorianCalendar();
            gc.setTime(date);
            jaxb.withDateTime(df.newXMLGregorianCalendar(gc));

            Object bean = wrap ? mObjectFactory.createNotificationEvent(jaxb) : jaxb;

            StringWriter sw = new StringWriter();

            try {
                mMarshallerLock.lock();
                getMarshaller(mObjectFactory.getClass().getPackage().getName()).marshal(bean, sw);
            } finally {
                mMarshallerLock.unlock();
            }
            return sw.toString();
        } catch (JAXBException e) {
            throw new NotificationFormatException(e);
        } catch (DatatypeConfigurationException e) {
            throw new NotificationFormatException(e);
        }
    }

    private ObjectNamesType toObjectNamesType(List<ObjectName> objectNameList) {
        List<String> roles = toStringList(objectNameList);
        ObjectNamesType ont = mObjectFactory.createObjectNamesType();
        ont.withObjectName(roles);
        return ont;
    }

    private DatatypeFactory getDatatypeFactory() throws DatatypeConfigurationException {
        if (mDatatypeFactory == null) {
            mDatatypeFactory = DatatypeFactory.newInstance();
        }
        return mDatatypeFactory;
    }

    private Marshaller getMarshaller(String aPackageName) throws JAXBException {
        if (mMarshaller == null) {
            mMarshaller = JAXBContext.newInstance(aPackageName).createMarshaller();
        }
        return mMarshaller;
    }

    private List<String> toStringList(List<ObjectName> objectNames) {
        List<String> roles = new ArrayList(objectNames.size());
        for (ObjectName on : objectNames) {
            roles.add(on.toString());
        }
        return roles;
    }
}
