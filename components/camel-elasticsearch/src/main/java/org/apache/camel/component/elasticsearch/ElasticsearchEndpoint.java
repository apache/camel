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
package org.apache.camel.component.elasticsearch;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.exists.ExistsRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

/**
 * The elasticsearch component is used for interfacing with ElasticSearch
 * server.
 */
@UriEndpoint(scheme = "elasticsearch", title = "Elasticsearch", syntax = "elasticsearch:clusterName", producerOnly = true, label = "monitoring,search")
public class ElasticsearchEndpoint extends DefaultEndpoint {

	private static final Logger LOG = LoggerFactory
			.getLogger(ElasticsearchEndpoint.class);

	private Node node;
	private Client client;
	private ElasticsearchHttpClient esHttpClient;
	private volatile boolean closeClient;
	@UriParam
	private ElasticsearchConfiguration configuration;
	private Boolean useHttpClient = false;

	public ElasticsearchEndpoint(String uri, ElasticsearchComponent component,
			ElasticsearchConfiguration config, Client client) throws Exception {
		super(uri, component);
		this.configuration = config;
		this.client = client;
		this.closeClient = client == null;
		this.useHttpClient = configuration.getUseHttpClient();
	}

	public Producer createProducer() throws Exception {
		return new ElasticsearchProducer(this);
	}

	public Consumer createConsumer(Processor processor) throws Exception {
		throw new UnsupportedOperationException(
				"Cannot consume from an ElasticsearchEndpoint: "
						+ getEndpointUri());
	}

	public boolean isSingleton() {
		return true;
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void doStart() throws Exception {
		super.doStart();

		if (configuration.getUseHttpClient()) {
			LOG.info("Using HTTP Client ");
			esHttpClient = new ElasticsearchHttpClient();
			esHttpClient.setHost(configuration.getIp());
			esHttpClient.setPort(String.valueOf(configuration.getPort()));
			return;
		} else {
			if (client == null) {
				if (configuration.isLocal()) {
					LOG.info("Starting local ElasticSearch server");
				} else {
					LOG.info("Joining ElasticSearch cluster "
							+ configuration.getClusterName());
				}

				if (configuration.getIp() != null) {
					this.client = TransportClient
							.builder()
							.settings(getSettings())
							.build()
							.addTransportAddress(
									new InetSocketTransportAddress(InetAddress
											.getByName(configuration.getIp()),
											configuration.getPort()));
				} else if (configuration.getTransportAddressesList() != null
						&& !configuration.getTransportAddressesList().isEmpty()) {
					List<TransportAddress> addresses = new ArrayList(
							configuration.getTransportAddressesList().size());
					for (TransportAddress address : configuration
							.getTransportAddressesList()) {
						addresses.add(address);
					}
					this.client = TransportClient
							.builder()
							.settings(getSettings())
							.build()
							.addTransportAddresses(
									addresses
											.toArray(new TransportAddress[addresses
													.size()]));
				} else {
					NodeBuilder builder = nodeBuilder().local(
							configuration.isLocal()).data(
							configuration.getData());
					if (configuration.isLocal()) {
						builder.getSettings().put("http.enabled", false);
					}
					if (!configuration.isLocal()
							&& configuration.getClusterName() != null) {
						builder.clusterName(configuration.getClusterName());
					}
					node = builder.node();
					client = node.client();
				}
			}
		}

	}

	private Settings getSettings() {
		return Settings
				.settingsBuilder()
				.put("cluster.name", configuration.getClusterName())
				.put("client.transport.ignore_cluster_name", false)
				.put("node.client", true)
				.put("client.transport.sniff",
						configuration.getClientTransportSniff())
				.put("http.enabled", false).build();
	}

	@Override
	protected void doStop() throws Exception {
		if (closeClient) {
			if (configuration.isLocal()) {
				LOG.info("Stopping local ElasticSearch server");
			} else {
				LOG.info("Leaving ElasticSearch cluster "
						+ configuration.getClusterName());
			}
			if (client != null)
				client.close();
			if (node != null) {
				node.close();
			}
			client = null;
			node = null;
		}
		super.doStop();
	}

	public Client getClient() {
		return client;
	}

	public ElasticsearchConfiguration getConfig() {
		return configuration;
	}

	public void setOperation(String operation) {
		configuration.setOperation(operation);
	}

	private String getIndexName(Message message) {
		return message.getHeader(ElasticsearchConstants.PARAM_INDEX_NAME,
				String.class);
	}

	private String getIndexType(Message message) {
		return message.getHeader(ElasticsearchConstants.PARAM_INDEX_TYPE,
				String.class);
	}

	public String index(Message message) {
		if (useHttpClient) {
			LOG.info("Indexing into Elasticsearch using HTTP Client ");
			esHttpClient.index(getIndexName(message), getIndexType(message),
					message.getBody(String.class));
		} else {
			IndexRequest indexRequest = message.getBody(IndexRequest.class);
			return client.index(indexRequest).actionGet().getId();
		}
		return null;
	}

	public void update(Message message) {
		UpdateRequest updateRequest = message.getBody(UpdateRequest.class);
		message.setBody(client.update(updateRequest).actionGet().getId());
	}

	public void get(Message message) {
		GetRequest getRequest = message.getBody(GetRequest.class);
		message.setBody(client.get(getRequest));
	}

	public void multiget(Message message) {
		MultiGetRequest multiGetRequest = message
				.getBody(MultiGetRequest.class);
		message.setBody(client.multiGet(multiGetRequest));
	}

	public void bulk(Message message) {
		BulkRequest bulkRequest = message.getBody(BulkRequest.class);
		message.setBody(client.bulk(bulkRequest).actionGet());
	}

	public List<String> bulkIndex(Message message) {
		List<String> indexedIds = new ArrayList<String>();

		if (useHttpClient) {
			List<Object> objects = message.getBody(List.class);
			List<String> documents = new ArrayList<String>(objects.size());
			for (Object obj : objects) {
				documents.add((String) obj);
			}
			return esHttpClient.bulkIndex(getIndexName(message),
					getIndexType(message), documents);
		} else {
			BulkRequest bulkRequest = message.getBody(BulkRequest.class);
			for (BulkItemResponse response : client.bulk(bulkRequest)
					.actionGet().getItems()) {
				indexedIds.add(response.getId());
			}
		}
		return indexedIds;
	}

	public void delete(Message message) {
		DeleteRequest deleteRequest = message.getBody(DeleteRequest.class);
		message.setBody(client.delete(deleteRequest).actionGet());
	}

	public void exists(Message message) {
		ExistsRequest existsRequest = message.getBody(ExistsRequest.class);
		message.setBody(client.admin().indices()
				.prepareExists(existsRequest.indices()).get().isExists());
	}

	public void search(Message message) {
		SearchRequest searchRequest = message.getBody(SearchRequest.class);
		message.setBody(client.search(searchRequest).actionGet());
	}

	public Object getById(Message message) {
		if (useHttpClient) {
			return esHttpClient.getById(getIndexName(message),
					getIndexType(message), message.getBody(String.class));
		} else {
			GetRequest getRequest = message.getBody(GetRequest.class);
			return client.get(getRequest);
		}
	}

}
