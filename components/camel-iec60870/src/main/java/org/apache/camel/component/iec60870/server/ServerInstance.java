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
package org.apache.camel.component.iec60870.server;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.component.iec60870.DiscardAckModule;
import org.apache.camel.component.iec60870.ObjectAddress;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.ASDUAddress;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.InformationObjectAddress;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.Value;
import org.eclipse.neoscada.protocol.iec60870.server.Server;
import org.eclipse.neoscada.protocol.iec60870.server.data.DataModule;
import org.eclipse.neoscada.protocol.iec60870.server.data.model.BackgroundModel;
import org.eclipse.neoscada.protocol.iec60870.server.data.model.ChangeDataModel;
import org.eclipse.neoscada.protocol.iec60870.server.data.model.ChangeModel;
import org.eclipse.neoscada.protocol.iec60870.server.data.model.WriteModel;
import org.eclipse.neoscada.protocol.iec60870.server.data.model.WriteModel.Action;
import org.eclipse.neoscada.protocol.iec60870.server.data.model.WriteModel.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Arrays.asList;

public class ServerInstance {
    private static final Logger LOG = LoggerFactory.getLogger(ServerInstance.class);

    private final ServerOptions options;

    private final class DataModelImpl extends ChangeDataModel {
        private DataModelImpl() {
            super("Camel/IEC60870/DataModel");
        }

        @Override
        protected ChangeModel createChangeModel() {
            if (ServerInstance.this.options.getBufferingPeriod() != null && ServerInstance.this.options.getBufferingPeriod() > 0) {
                LOG.info("Creating buffering change model: {} ms", ServerInstance.this.options.getBufferingPeriod());
                return makeBufferingChangeModel(ServerInstance.this.options.getBufferingPeriod());
            } else {
                LOG.info("Creating instant change model");
                return makeInstantChangeModel();
            }
        }

        @Override
        protected WriteModel createWriteModel() {
            return new WriteModel() {

                @Override
                public Action prepareCommand(final Request<Boolean> request) {
                    return prepareAction(request);
                }

                @Override
                public Action prepareSetpointFloat(final Request<Float> request) {
                    return prepareAction(request);
                }

                @Override
                public Action prepareSetpointScaled(final Request<Short> request) {
                    return prepareAction(request);
                }
            };
        }

        @Override
        protected BackgroundModel createBackgroundModel() {
            if (ServerInstance.this.options.getBackgroundScanPeriod() > 0) {
                LOG.info("Creating background scan model: {} ms", ServerInstance.this.options.getBackgroundScanPeriod());
                return makeDefaultBackgroundModel();
            }
            LOG.info("Not creating background scan model");
            return null;
        }

        @Override
        public void notifyDataChange(final ASDUAddress asduAddress, final InformationObjectAddress informationObjectAddress, final Value<?> value, final boolean notify) {
            super.notifyDataChange(asduAddress, informationObjectAddress, value, notify);
        }
    }

    @FunctionalInterface
    public interface ServerObjectListener {
        CompletionStage<Void> execute(Request<?> request);
    }

    private final DataModelImpl dataModel = new DataModelImpl();

    private Server server;
    private DataModule dataModule;
    private final InetSocketAddress address;
    private final Map<ObjectAddress, ServerObjectListener> listeners = new ConcurrentHashMap<>();

    public ServerInstance(final String host, final int port, final ServerOptions options) throws UnknownHostException {
        this.options = options;
        this.address = new InetSocketAddress(InetAddress.getByName(host), port);
    }

    public void start() {
        this.dataModel.start();
        this.dataModule = new DataModule(this.options.getDataModuleOptions(), this.dataModel);
        this.server = new Server(this.address, this.options.getProtocolOptions(), asList(this.dataModule, new DiscardAckModule()));
    }

    public void stop() {
        final LinkedList<Exception> ex = new LinkedList<>();

        if (this.server != null) {
            try {
                this.server.close();
            } catch (final Exception e) {
                ex.add(e);
            }
            this.server = null;
        }
        if (this.dataModule != null) {
            try {
                this.dataModule.dispose();
            } catch (final Exception e) {
                ex.add(e);
            }
            this.dataModule = null;
        }

        // handle all exceptions

        final Exception e = ex.pollFirst();
        if (e != null) {
            RuntimeException re;
            if (e instanceof RuntimeException) {
                re = (RuntimeException)e;
            } else {
                re = new RuntimeException(e);
            }
            ex.forEach(re::addSuppressed);
            throw re;
        }
    }

    private Action prepareAction(final Request<?> request) {
        final ObjectAddress address = ObjectAddress.valueOf(request.getHeader().getAsduAddress(), request.getAddress());
        final ServerObjectListener listener = this.listeners.get(address);

        if (listener == null) {
            // no one is listening
            return null;
        }

        return () -> listener.execute(request);
    }

    public void setListener(final ObjectAddress address, final ServerObjectListener listener) {
        Objects.requireNonNull(address);

        if (listener != null) {
            this.listeners.put(address, listener);
        } else {
            this.listeners.remove(address);
        }
    }

    public void notifyValue(final ObjectAddress address, final Value<?> value) {
        Objects.requireNonNull(address);
        Objects.requireNonNull(value);

        this.dataModel.notifyDataChange(address.getASDUAddress(), address.getInformationObjectAddress(), value, true);
    }
}
