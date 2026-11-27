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
package org.apache.camel.component.file.remote;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.util.function.ThrowingRunnable;

/**
 * Remote file operations based on some backing framework
 */
public interface RemoteFileOperations<T> extends GenericFileOperations<T> {

    /**
     * Connects to the remote server
     *
     * @param  configuration                       configuration
     * @param  exchange                            the exchange that trigger the connect (if any)
     * @return                                     <tt>true</tt> if connected
     * @throws GenericFileOperationFailedException can be thrown
     */
    boolean connect(RemoteFileConfiguration configuration, Exchange exchange) throws GenericFileOperationFailedException;

    /**
     * Returns whether we are connected to the remote server or not
     *
     * @return                                     <tt>true</tt> if connected, <tt>false</tt> if not
     * @throws GenericFileOperationFailedException can be thrown
     */
    boolean isConnected() throws GenericFileOperationFailedException;

    /**
     * Disconnects from the remote server
     *
     * @throws GenericFileOperationFailedException can be thrown
     */
    void disconnect() throws GenericFileOperationFailedException;

    /**
     * Disconnects from the remote server when the producer or the consumer stops.
     * <p>
     * Another thread can still run an operation at that time: an upload of the producer, or the poll of the consumer.
     * As long as the shutdown of Camel is not forced, such as when a producer cache evicts the producer, this method
     * should wait for it, as {@link #disconnect()} does: it can be a healthy but slow upload. Once the shutdown is
     * forced, the operation is one that the shutdown gave up waiting for, such as an upload that hangs as long as the
     * server does not answer: unlike {@link #disconnect()}, this method should then not wait for it without bound, but
     * disconnect anyway, so that the operation fails.
     * <p>
     * The default implementation disconnects as {@link #disconnect()} does, when connected.
     *
     * @throws GenericFileOperationFailedException can be thrown
     */
    default void disconnectOnStop() throws GenericFileOperationFailedException {
        if (isConnected()) {
            disconnect();
        }
    }

    /**
     * Forces a hard disconnect from the remote server and cause the client to be re-created on next poll.
     *
     * @throws GenericFileOperationFailedException can be thrown
     */
    void forceDisconnect() throws GenericFileOperationFailedException;

    /**
     * Sends a noop command to the remote server
     *
     * @return                                     <tt>true</tt> if the noop was a success, <tt>false</tt> otherwise
     * @throws GenericFileOperationFailedException can be thrown
     */
    boolean sendNoop() throws GenericFileOperationFailedException;

    /**
     * Whether the server completed an operation on this connection so recently that a noop before the next upload would
     * not show anything new about the connection. The default returns <tt>false</tt>, so the producer always sends the
     * noop.
     *
     * @return <tt>true</tt> if the producer can skip the noop before the next upload
     * @since  4.24
     */
    default boolean isRecentlyUsed() {
        return false;
    }

    /**
     * Runs a check of the connection, such as the noop of the producer health check, unless another thread runs an
     * operation on the connection at this time, such as an upload of the producer. A health check must not wait for
     * such an operation: an upload uses the connection for as long as the transfer takes, and on a server that does not
     * answer, up to the timeout of the connection.
     * <p>
     * The default implementation always runs the check.
     *
     * @param  check     the check, which can call the operations
     * @return           {@code true} if the check ran, {@code false} if another thread was using the connection
     * @throws Exception thrown by the check
     * @since            4.24
     */
    default boolean runIfIdle(ThrowingRunnable<Exception> check) throws Exception {
        check.run();
        return true;
    }

    /**
     * Sends a site command to the remote server
     *
     * @param  command                             the command
     * @return                                     <tt>true</tt> if the command was a success, <tt>false</tt> otherwise
     * @throws GenericFileOperationFailedException can be thrown
     */
    boolean sendSiteCommand(String command) throws GenericFileOperationFailedException;

}
