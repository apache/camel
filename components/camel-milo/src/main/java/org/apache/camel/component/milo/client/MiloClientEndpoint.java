/*
 * Copyright (C) 2016 Jens Reimann <jreimann@redhat.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.component.milo.client;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

import java.util.Objects;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.milo.NamespaceId;
import org.apache.camel.component.milo.PartialNodeId;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;

@UriEndpoint(scheme = "milo-client", syntax = "milo-client:tcp://user:password@host:port/path/to/service?itemId=item.id&namespaceUri=urn:foo:bar", title = "Milo based OPC UA Client", consumerClass = MiloClientConsumer.class, label = "iot")
public class MiloClientEndpoint extends DefaultEndpoint implements MiloClientItemConfiguration {

	/**
	 * The OPC UA server endpoint
	 */
	@UriPath
	@Metadata(required = "true")
	private final String endpointUri;

	/**
	 * The node ID as string ID **deprecated**
	 *
	 * @deprecated Use "node" instead
	 */
	@UriParam
	@Deprecated
	private String nodeId;

	/**
	 * The namespace as URI **deprecated**
	 *
	 * @deprecated Use "node" instead
	 */
	@UriParam
	@Deprecated
	private String namespaceUri;

	/**
	 * The namespace as numeric index **deprecated**
	 *
	 * @deprecated Use "node" instead
	 */
	@UriParam
	@Deprecated
	private Integer namespaceIndex;

	/**
	 * The node definition (see Node ID)
	 */
	@UriParam
	private ExpandedNodeId node;

	/**
	 * The sampling interval in milliseconds
	 */
	@UriParam
	private Double samplingInterval;

	/**
	 * The client configuration
	 */
	@UriParam
	private MiloClientConfiguration client;

	/**
	 * Default "await" setting for writes
	 */
	@UriParam
	private boolean defaultAwaitWrites = false;

	private final MiloClientConnection connection;
	private final MiloClientComponent component;

	public MiloClientEndpoint(final String uri, final MiloClientComponent component,
			final MiloClientConnection connection, final String endpointUri) {
		super(uri, component);

		Objects.requireNonNull(component);
		Objects.requireNonNull(connection);
		Objects.requireNonNull(endpointUri);

		this.endpointUri = endpointUri;

		this.component = component;
		this.connection = connection;
	}

	@Override
	protected void doStart() throws Exception {
		super.doStart();
	}

	@Override
	protected void doStop() throws Exception {
		this.component.disposed(this);
		super.doStop();
	}

	@Override
	public Producer createProducer() throws Exception {
		return new MiloClientProducer(this, this.connection, this, this.defaultAwaitWrites);
	}

	@Override
	public Consumer createConsumer(final Processor processor) throws Exception {
		return new MiloClientConsumer(this, processor, this.connection, this);
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

	public MiloClientConnection getConnection() {
		return this.connection;
	}

	// item configuration

	@Override
	public PartialNodeId makePartialNodeId() {
		PartialNodeId result = null;

		if (this.node != null) {
			result = PartialNodeId.fromExpandedNodeId(this.node);
		}

		if (result == null && this.nodeId != null) {
			result = new PartialNodeId(this.nodeId);
		}

		if (result == null) {
			throw new IllegalStateException("Missing or invalid node id configuration");
		} else {
			return result;
		}
	}

	@Override
	public NamespaceId makeNamespaceId() {
		NamespaceId result = null;

		if (this.node != null) {
			result = NamespaceId.fromExpandedNodeId(this.node);
		}

		if (result == null && this.namespaceIndex != null) {
			result = new NamespaceId(ushort(this.namespaceIndex));
		}
		if (result == null && this.namespaceUri != null) {
			result = new NamespaceId(this.namespaceUri);
		}

		if (result == null) {
			throw new IllegalStateException("Missing or invalid node id configuration");
		} else {
			return result;
		}
	}

	public String getNodeId() {
		return this.nodeId;
	}

	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}

	public String getNamespaceUri() {
		return this.namespaceUri;
	}

	public void setNamespaceUri(final String namespaceUri) {
		this.namespaceUri = namespaceUri;
	}

	public Integer getNamespaceIndex() {
		return this.namespaceIndex;
	}

	public void setNamespaceIndex(final int namespaceIndex) {
		this.namespaceIndex = namespaceIndex;
	}

	public void setNode(final String node) {
		if (node == null) {
			this.node = null;
		} else {
			this.node = ExpandedNodeId.parse(node);
		}
	}

	public String getNode() {
		if (this.node != null) {
			return this.node.toParseableString();
		} else {
			return null;
		}
	}

	@Override
	public Double getSamplingInterval() {
		return this.samplingInterval;
	}

	public void setSamplingInterval(final Double samplingInterval) {
		this.samplingInterval = samplingInterval;
	}

	public boolean isDefaultAwaitWrites() {
		return this.defaultAwaitWrites;
	}

	public void setDefaultAwaitWrites(final boolean defaultAwaitWrites) {
		this.defaultAwaitWrites = defaultAwaitWrites;
	}
}
