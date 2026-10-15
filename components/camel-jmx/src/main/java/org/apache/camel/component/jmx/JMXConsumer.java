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
package org.apache.camel.component.jmx;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer will add itself as a NotificationListener on the object specified by the objectName param.
 */
public class JMXConsumer extends DefaultConsumer implements NotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(JMXConsumer.class);

    private JMXEndpoint mJmxEndpoint;
    private volatile JMXConnector mConnector;
    private volatile String mConnectionId;

    /**
     * Used for processing notifications (should not block notification thread)
     */
    private ExecutorService executorService;
    private boolean shutdownExecutorService;

    /**
     * Used to schedule delayed connection attempts
     */
    private volatile ScheduledExecutorService mScheduledExecutor;

    /**
     * Whether a reconnect is scheduled and has not begun: a lost connection is reported more than once (the connector
     * sends "failed" and then "closed" for it) and must be followed by one reconnect
     */
    private final AtomicBoolean mReconnectPending = new AtomicBoolean();

    /**
     * Used to receive notifications about lost connections
     */
    private ConnectionNotificationListener mConnectionNotificationListener;

    /**
     * connection to the mbean server (local or remote)
     */
    private MBeanServerConnection mServerConnection;

    /**
     * used to format Notification objects as xml
     */
    private NotificationXmlFormatter mFormatter;

    public JMXConsumer(JMXEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.mJmxEndpoint = endpoint;
        this.mFormatter = new NotificationXmlFormatter();
    }

    @Override
    public JMXEndpoint getEndpoint() {
        return (JMXEndpoint) super.getEndpoint();
    }

    /**
     * Initializes the mbean server connection and starts listening for Notification events from the object.
     */
    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(mFormatter);

        if (executorService == null) {
            if (getEndpoint().getExecutorService() != null) {
                // use shared thread-pool
                executorService = getEndpoint().getExecutorService();
            } else {
                // lets just use a single threaded thread-pool to process these notifications
                String name = "JMXConsumer[" + getEndpoint().getJMXObjectName().getCanonicalName() + "]";
                executorService
                        = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, name);
                shutdownExecutorService = true;
            }
        }

        // connect to the mbean server
        if (mJmxEndpoint.isPlatformServer()) {
            setServerConnection(ManagementFactory.getPlatformMBeanServer());
        } else {
            try {
                initNetworkConnection();
            } catch (IOException e) {
                if (!mJmxEndpoint.isTestConnectionOnStartup()) {
                    LOG.warn("Failed to connect to JMX server. >> {}", e.getMessage());
                    scheduleDelayedStart();
                    return;
                } else {
                    throw e;
                }
            }
        }
        // subscribe
        addNotificationListener();
        super.doStart();
    }

    /**
     * Initializes a network connection to the configured JMX server and registers a connection notification listener to
     * to receive notifications of connection loss
     */
    private void initNetworkConnection() throws IOException {
        if (mConnector != null) {
            try {
                // this consumer closes the connection itself, so its "closed" notification is not a lost connection:
                // without the listener it cannot schedule a reconnect for a connection that is being replaced
                mConnector.removeConnectionNotificationListener(getConnectionNotificationListener());
            } catch (Exception e) {
                // ignore, the listener is not registered on a connector that could not be opened
            }
            try {
                mConnector.close();
            } catch (Exception e) {
                // ignore, as this is best effort
            }
        }
        JMXServiceURL url = new JMXServiceURL(mJmxEndpoint.getServerURL());
        String[] creds = { mJmxEndpoint.getUser(), mJmxEndpoint.getPassword() };
        Map<String, String[]> map = Collections.singletonMap(JMXConnector.CREDENTIALS, creds);
        mConnector = JMXConnectorFactory.connect(url, map);
        mConnector.addConnectionNotificationListener(getConnectionNotificationListener(), null, null);
        mConnectionId = mConnector.getConnectionId();
        setServerConnection(mConnector.getMBeanServerConnection());
    }

    /**
     * Returns the connection notification listener; creates the default listener if one does not already exist
     */
    protected ConnectionNotificationListener getConnectionNotificationListener() {
        if (mConnectionNotificationListener == null) {
            mConnectionNotificationListener = new ConnectionNotificationListener();
        }
        return mConnectionNotificationListener;
    }

    /**
     * Schedules execution of the doStart() operation to occur again after the reconnect delay
     */
    protected void scheduleDelayedStart() {
        Runnable startRunnable = () -> {
            try {
                doStart();
            } catch (Exception e) {
                LOG.error("An unrecoverable exception has occurred while starting the JMX consumer"
                          + " for endpoint {}",
                        URISupport.sanitizeUri(mJmxEndpoint.getEndpointUri()), e);
            }
        };
        LOG.info("Delaying JMX consumer startup for endpoint {}. Trying again in {} seconds.",
                URISupport.sanitizeUri(mJmxEndpoint.getEndpointUri()), mJmxEndpoint.getReconnectDelay());
        getExecutor().schedule(startRunnable, mJmxEndpoint.getReconnectDelay(), TimeUnit.SECONDS);
    }

    /**
     * Helper class used for receiving connection loss notifications
     */
    private class ConnectionNotificationListener implements NotificationListener {

        @Override
        public void handleNotification(Notification notification, Object handback) {
            JMXConnectionNotification connectionNotification = (JMXConnectionNotification) notification;
            if (connectionNotification.getType().equals(JMXConnectionNotification.OPENED)) {
                connectorReconnected(connectionNotification.getConnectionId());
                return;
            }
            // only reset the connection if the notification is for the connection from this endpoint
            if (!connectionNotification.getConnectionId().equals(mConnectionId)) {
                return;
            }
            if (!isRunAllowed()) {
                // a notification that arrives after the consumer was stopped
                return;
            }
            if (connectionNotification.getType().equals(JMXConnectionNotification.NOTIFS_LOST)
                    || connectionNotification.getType().equals(JMXConnectionNotification.CLOSED)
                    || connectionNotification.getType().equals(JMXConnectionNotification.FAILED)) {
                LOG.warn("Lost JMX connection for : {}", URISupport.sanitizeUri(mJmxEndpoint.getEndpointUri()));
                if (mJmxEndpoint.isReconnectOnConnectionFailure()) {
                    scheduleReconnect();
                } else {
                    LOG.warn("The JMX consumer will not be reconnected. Use 'reconnectOnConnectionFailure' to "
                             + "enable reconnections.");
                }
            }
        }
    }

    /**
     * The connector lost its connection and opened another one by itself (the listener is added after the connector is
     * connected, so the first connection is not reported here). The new connection has another id, which the following
     * notifications carry, and the connector has registered the listener of this consumer again only if the MBean
     * existed at that moment. So the consumer subscribes again on that connection, and connects again only if that
     * fails.
     */
    private void connectorReconnected(String connectionId) {
        if (connectionId == null || connectionId.equals(mConnectionId)) {
            return;
        }
        mConnectionId = connectionId;
        if (!isRunAllowed()) {
            return;
        }
        if (mJmxEndpoint.isReconnectOnConnectionFailure()) {
            LOG.warn("The JMX connector has opened a new connection for: {}. Subscribing again on that connection.",
                    URISupport.sanitizeUri(mJmxEndpoint.getEndpointUri()));
            scheduleReconnect(true);
        } else {
            LOG.warn("The JMX connector has opened a new connection for: {}. The JMX consumer may not be subscribed"
                     + " anymore. Use 'reconnectOnConnectionFailure' to enable reconnections.",
                    URISupport.sanitizeUri(mJmxEndpoint.getEndpointUri()));
        }
    }

    /**
     * Schedules an attempt to re-initialize a lost connection after the reconnect delay, unless an attempt is already
     * scheduled or the consumer is stopped
     */
    protected void scheduleReconnect() {
        scheduleReconnect(false);
    }

    /**
     * Schedules an attempt to re-initialize the connection after the reconnect delay, unless an attempt is already
     * scheduled or the consumer is stopped.
     *
     * @param resubscribeFirst whether the attempt first subscribes again on the current connection, which the connector
     *                         has restored by itself, and connects again only when that fails
     */
    private void scheduleReconnect(boolean resubscribeFirst) {
        if (!isRunAllowed() || !mReconnectPending.compareAndSet(false, true)) {
            return;
        }
        Runnable startRunnable = () -> {
            // from here on a lost connection needs another attempt, also when it is reported while this one runs
            mReconnectPending.set(false);
            if (!isRunAllowed()) {
                return;
            }
            if (resubscribeFirst && resubscribe()) {
                return;
            }
            try {
                initNetworkConnection();
                addNotificationListener();
            } catch (Exception e) {
                LOG.warn("Failed to reconnect to JMX server. >> {}", e.getMessage());
                scheduleReconnect();
            }
        };
        LOG.info("Delaying JMX consumer reconnection for endpoint {}. Trying again in {} seconds.",
                URISupport.sanitizeUri(mJmxEndpoint.getEndpointUri()), mJmxEndpoint.getReconnectDelay());
        try {
            getExecutor().schedule(startRunnable, mJmxEndpoint.getReconnectDelay(), TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            mReconnectPending.set(false);
            throw e;
        }
    }

    /**
     * Subscribes again on the current connection: removes every registration of this listener (the connector has
     * registered it again if the MBean existed when it reconnected) and adds one. So the consumer is subscribed once,
     * without closing a connection that works.
     *
     * @return false if that failed, then the connection has to be replaced
     */
    private boolean resubscribe() {
        try {
            try {
                getServerConnection().removeNotificationListener(getEndpoint().getJMXObjectName(), this);
            } catch (ListenerNotFoundException e) {
                // the connector did not register the listener again
            }
            addNotificationListener();
            return true;
        } catch (Exception e) {
            LOG.warn("Failed to subscribe again on the connection opened by the JMX connector, reconnecting. >> {}",
                    e.getMessage());
            return false;
        }
    }

    /**
     * Returns the thread executor used for scheduling delayed connection events. Creates the executor if it does not
     * already exist
     */
    private ScheduledExecutorService getExecutor() {
        if (this.mScheduledExecutor == null) {
            mScheduledExecutor = mJmxEndpoint.getCamelContext().getExecutorServiceManager()
                    .newSingleThreadScheduledExecutor(this, "JMXConnectionExecutor");
        }
        return mScheduledExecutor;
    }

    /**
     * Adds a notification listener to the target bean.
     */
    protected void addNotificationListener() throws Exception {
        JMXEndpoint ep = getEndpoint();
        NotificationFilter nf = ep.getNotificationFilter();

        // if we should observe a single attribute then use filter
        if (nf == null && ep.getObservedAttribute() != null) {
            LOG.debug("Observing attribute: {}", ep.getObservedAttribute());
            boolean match = !ep.isNotifyDiffer();
            nf = new JMXConsumerNotificationFilter(ep.getObservedAttribute(), ep.getStringToCompare(), match);
        }

        ObjectName objectName = ep.getJMXObjectName();

        getServerConnection().addNotificationListener(objectName, this, nf, ep.getHandback());
    }

    /**
     * Removes the notification listeners and terminates the background connection polling process if it exists
     */
    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (mScheduledExecutor != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(mScheduledExecutor);
            mScheduledExecutor = null;
        }
        // the scheduled reconnect, if there was one, is cancelled
        mReconnectPending.set(false);

        removeNotificationListeners();

        if (mConnector != null) {
            mConnector.close();
        }

        ServiceHelper.stopService(mFormatter);

        if (shutdownExecutorService && executorService != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
            executorService = null;
        }
    }

    /**
     * Removes the configured notification listener and the connection notification listener from the connection
     */
    protected void removeNotificationListeners() throws Exception {
        getServerConnection().removeNotificationListener(mJmxEndpoint.getJMXObjectName(), this);
        if (mConnectionNotificationListener != null) {
            mConnector.removeConnectionNotificationListener(mConnectionNotificationListener);
            mConnectionNotificationListener = null;
        }
    }

    protected MBeanServerConnection getServerConnection() {
        return mServerConnection;
    }

    protected void setServerConnection(MBeanServerConnection aServerConnection) {
        mServerConnection = aServerConnection;
    }

    /**
     * Processes the Notification received. The handback will be set as the header {@link JMXConstants#JMX_HANDBACK}
     * while the Notification will be set as the body.
     * <p/>
     * If the format is set to "xml" then the Notification will be converted to XML first using
     * {@link NotificationXmlFormatter}
     *
     * @see javax.management.NotificationListener#handleNotification(javax.management.Notification, java.lang.Object)
     */
    @Override
    public void handleNotification(Notification aNotification, Object aHandback) {
        JMXEndpoint ep = getEndpoint();
        Exchange exchange = createExchange(true);
        Message message = exchange.getIn();
        message.setHeader(JMXConstants.JMX_HANDBACK, aHandback);
        try {
            if (ep.isXML()) {
                message.setBody(getFormatter().format(aNotification));
            } else {
                message.setBody(aNotification);
            }

            // process the notification from thread pool to not block this notification callback thread from the JVM
            executorService.submit(() -> {
                try {
                    getProcessor().process(exchange);
                } catch (Exception e) {
                    getExceptionHandler().handleException("Failed to process notification", e);
                }
            });

        } catch (NotificationFormatException e) {
            getExceptionHandler().handleException("Failed to marshal notification", e);
        }
    }

    protected NotificationXmlFormatter getFormatter() {
        return mFormatter;
    }
}
