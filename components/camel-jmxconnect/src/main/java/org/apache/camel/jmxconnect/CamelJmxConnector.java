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
package org.apache.camel.jmxconnect;


import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spring.remoting.CamelProxyFactoryBean;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.management.*;
import javax.management.remote.*;
import javax.security.auth.Subject;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>
 * The client end of a JMX API connector. An object of this type can be used to establish a connection to a connector
 * server.
 * </p>
 * <p/>
 * <p>
 * A newly-created object of this type is unconnected. Its {@link #connect()} method must be called before it can
 * be used. However, objects created by {@link JMXConnectorFactory#connect(JMXServiceURL, Map)
 * JMXConnectorFactory.connect} are already connected.
 * </p>
 *
 * @version $Revision$
 */
public class CamelJmxConnector implements JMXConnector, CamelContextAware {
    private static final Log log = LogFactory.getLog(JMXConnector.class);
    private NotificationBroadcasterSupport connectionNotifier = new NotificationBroadcasterSupport();
    private AtomicLong notificationNumber = new AtomicLong();
    private Map env;
    private String endpointUri;
    private CamelProxyFactoryBean proxy;
    private MBeanCamelServerConnectionClient client;
    private boolean connected;
    private CamelContext camelContext;
    private String connectionId;

    public CamelJmxConnector(Map env, String endpointUri) {
        this.env = env;
        this.endpointUri = endpointUri;
    }

    public CamelJmxConnector(Map env, JMXServiceURL url) throws IOException {
        this(env, CamelJmxConnectorSupport.getEndpointUri(url, "camel"));
        // set any props in the url
        // TODO
        // populateProperties(this, endpointUri);
    }

    /**
     * <p>
     * Establishes the connection to the connector server. This method is equivalent to {@link #connect(Map)
     * connect(null)}.
     * </p>
     *
     * @throws IOException       if the connection could not be made because of a communication problem.
     * @throws SecurityException if the connection could not be made for security reasons.
     */
    public void connect() throws IOException {
        connect(this.env);
    }

    /**
     * <p>
     * Establishes the connection to the connector server.
     * </p>
     * <p/>
     * <p>
     * If <code>connect</code> has already been called successfully on this object, calling it again has no effect.
     * If, however, {@link #close} was called after <code>connect</code>, the new <code>connect</code> will throw
     * an <code>IOException</code>.
     * <p>
     * <p/>
     * <p>
     * Otherwise, either <code>connect</code> has never been called on this object, or it has been called but produced
     * an exception. Then calling <code>connect</code> will attempt to establish a connection to the connector server.
     * </p>
     *
     * @param env the properties of the connection. Properties in this map override properties in the map specified when
     *            the <code>JMXConnector</code> was created, if any. This parameter can be null, which is equivalent
     *            to an empty map.
     * @throws IOException       if the connection could not be made because of a communication problem.
     * @throws SecurityException if the connection could not be made for security reasons.
     */
    public void connect(Map env) throws IOException {
        if (!connected) {
            try {
                proxy = new CamelProxyFactoryBean();
                proxy.setCamelContext(getCamelContext());
                proxy.setServiceInterface(javax.management.MBeanServerConnection.class);
                proxy.setServiceInterface(MBeanCamelServerConnection.class);
                proxy.setServiceUrl(endpointUri);
                proxy.afterPropertiesSet();

                client = new MBeanCamelServerConnectionClient((MBeanCamelServerConnection) proxy.getObject() /* TODO */);
                connectionId = client.generateId();

                // TODO we need to establish a replyToEndpoint and inform the server!

                sendConnectionNotificationOpened();
            } catch (Exception e) {
                log.error("Failed to connect: " + e, e);
                IOException ioe = new IOException(e.getMessage());
                throw ioe;
            }
            connected = true;
        }
    }

    /**
     * <p>
     * Returns an <code>MBeanServerConnection</code> object representing a remote MBean server. For a given
     * <code>JMXConnector</code>, two successful calls to this method will usually return the same
     * <code>MBeanServerConnection</code> object, though this is not required.
     * </p>
     * <p/>
     * <p>
     * For each method in the returned <code>MBeanServerConnection</code>, calling the method causes the
     * corresponding method to be called in the remote MBean server. The value returned by the MBean server method is
     * the value returned to the client. If the MBean server method produces an <code>Exception</code>, the same
     * <code>Exception</code> is seen by the client. If the MBean server method, or the attempt to call it, produces
     * an <code>Error</code>, the <code>Error</code> is wrapped in a {@link JMXServerErrorException}, which is
     * seen by the client.
     * </p>
     * <p/>
     * <p>
     * Calling this method is equivalent to calling
     * {@link #getMBeanServerConnection(Subject) getMBeanServerConnection(null)} meaning that no delegation subject is
     * specified and that all the operations called on the <code>MBeanServerConnection</code> must use the
     * authenticated subject, if any.
     * </p>
     *
     * @return an object that implements the <code>MBeanServerConnection</code> interface by forwarding its methods to
     *         the remote MBean server.
     */
    public MBeanServerConnection getMBeanServerConnection() {
        return client;
    }

    /**
     * <p>
     * Returns an <code>MBeanServerConnection</code> object representing a remote MBean server on which operations are
     * performed on behalf of the supplied delegation subject. For a given <code>JMXConnector</code> and
     * <code>Subject</code>, two successful calls to this method will usually return the same
     * <code>MBeanServerConnection</code> object, though this is not required.
     * </p>
     * <p/>
     * <p>
     * For each method in the returned <code>MBeanServerConnection</code>, calling the method causes the
     * corresponding method to be called in the remote MBean server on behalf of the given delegation subject instead of
     * the authenticated subject. The value returned by the MBean server method is the value returned to the client. If
     * the MBean server method produces an <code>Exception</code>, the same <code>Exception</code> is seen by the
     * client. If the MBean server method, or the attempt to call it, produces an <code>Error</code>, the
     * <code>Error</code> is wrapped in a {@link JMXServerErrorException}, which is seen by the client.
     * </p>
     *
     * @param delegationSubject the <code>Subject</code> on behalf of which requests will be performed. Can be null, in which case
     *                          requests will be performed on behalf of the authenticated Subject, if any.
     * @return an object that implements the <code>MBeanServerConnection</code> interface by forwarding its methods to
     *         the remote MBean server on behalf of a given delegation subject.
     */
    public MBeanServerConnection getMBeanServerConnection(Subject delegationSubject) {
        throw new UnsupportedOperationException();
    }

    /**
     * <p>
     * Closes the client connection to its server. Any ongoing or new request using the MBeanServerConnection returned
     * by {@link #getMBeanServerConnection()} will get an <code>IOException</code>.
     * </p>
     * <p/>
     * <p>
     * If <code>close</code> has already been called successfully on this object, calling it again has no effect. If
     * <code>close</code> has never been called, or if it was called but produced an exception, an attempt will be
     * made to close the connection. This attempt can succeed, in which case <code>close</code> will return normally,
     * or it can generate an exception.
     * </p>
     * <p/>
     * <p>
     * Closing a connection is a potentially slow operation. For example, if the server has crashed, the close operation
     * might have to wait for a network protocol timeout. Callers that do not want to block in a close operation should
     * do it in a separate thread.
     * </p>
     *
     * @throws IOException if the connection cannot be closed cleanly. If this exception is thrown, it is not known whether
     *                     the server end of the connection has been cleanly closed.
     */
    public void close() throws IOException {
        if (connected) {
            connected = false;
            try {
                sendConnectionNotificationClosed();
                proxy.destroy();
            } catch (Exception e) {
                log.error("Failed to destroy proxy: " + e, e);
                throw new IOException(e.getMessage());
            }
        }
    }

    /**
     * <p>
     * Adds a listener to be informed of changes in connection status. The listener will receive notifications of type
     * {@link JMXConnectionNotification}. An implementation can send other types of notifications too.
     * </p>
     * <p/>
     * <p>
     * Any number of listeners can be added with this method. The same listener can be added more than once with the
     * same or different values for the filter and handback. There is no special treatment of a duplicate entry. For
     * example, if a listener is registered twice with no filter, then its <code>handleNotification</code> method will
     * be called twice for each notification.
     * </p>
     *
     * @param listener a listener to receive connection status notifications.
     * @param filter   a filter to select which notifications are to be delivered to the listener, or null if all
     *                 notifications are to be delivered.
     * @param handback an object to be given to the listener along with each notification. Can be null.
     * @throws NullPointerException if <code>listener</code> is null.
     * @see #removeConnectionNotificationListener
     * @see NotificationBroadcaster#addNotificationListener
     */
    public void addConnectionNotificationListener(NotificationListener listener, NotificationFilter filter,
                                                  Object handback) {
        connectionNotifier.addNotificationListener(listener, filter, handback);
    }

    /**
     * <p>
     * Removes a listener from the list to be informed of changes in status. The listener must previously have been
     * added. If there is more than one matching listener, all are removed.
     * </p>
     *
     * @param listener a listener to receive connection status notifications.
     * @throws NullPointerException      if <code>listener</code> is null.
     * @throws ListenerNotFoundException if the listener is not registered with this <code>JMXConnector</code>.
     * @see #removeConnectionNotificationListener(NotificationListener, NotificationFilter, Object)
     * @see #addConnectionNotificationListener
     * @see NotificationEmitter#removeNotificationListener
     */
    public void removeConnectionNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
        connectionNotifier.removeNotificationListener(listener);
    }

    /**
     * <p>
     * Removes a listener from the list to be informed of changes in status. The listener must previously have been
     * added with the same three parameters. If there is more than one matching listener, only one is removed.
     * </p>
     *
     * @param l        a listener to receive connection status notifications.
     * @param f        a filter to select which notifications are to be delivered to the listener. Can be null.
     * @param handback an object to be given to the listener along with each notification. Can be null.
     * @throws ListenerNotFoundException if the listener is not registered with this <code>JMXConnector</code>, or is not registered
     *                                   with the given filter and handback.
     * @see #removeConnectionNotificationListener(NotificationListener)
     * @see #addConnectionNotificationListener
     * @see NotificationEmitter#removeNotificationListener
     */
    public void removeConnectionNotificationListener(NotificationListener l, NotificationFilter f, Object handback)
            throws ListenerNotFoundException {
        connectionNotifier.removeNotificationListener(l, f, handback);
    }


    /**
     * <p>
     * Gets this connection's ID from the connector server. For a given connector server, every connection will have a
     * unique id which does not change during the lifetime of the connection.
     * </p>
     *
     * @return the unique ID of this connection. This is the same as the ID that the connector server includes in its
     *         {@link JMXConnectionNotification}s. The {@link javax.management.remote package description} describes the
     *         conventions for connection IDs.
     */
    public String getConnectionId() {
        return connectionId;
    }

    public CamelContext getCamelContext() {
        if (camelContext == null) {
            log.warn("No CamelContext injected so creating a default implementation");
            // TODO should we barf or create a default one?
            camelContext = new DefaultCamelContext();
        }
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    private void sendConnectionNotificationOpened() {
        JMXConnectionNotification notification = new JMXConnectionNotification(JMXConnectionNotification.OPENED, this,
                getConnectionId(), notificationNumber.incrementAndGet(), "Connection opened", null);
        connectionNotifier.sendNotification(notification);
    }

    private void sendConnectionNotificationClosed() {
        JMXConnectionNotification notification = new JMXConnectionNotification(JMXConnectionNotification.CLOSED, this,
                getConnectionId(), notificationNumber.incrementAndGet(), "Connection closed", null);
        connectionNotifier.sendNotification(notification);
    }

    private void sendConnectionNotificationFailed(String message) {
        JMXConnectionNotification notification = new JMXConnectionNotification(JMXConnectionNotification.FAILED, this,
                getConnectionId(), notificationNumber.incrementAndGet(), message, null);
        connectionNotifier.sendNotification(notification);
    }
}