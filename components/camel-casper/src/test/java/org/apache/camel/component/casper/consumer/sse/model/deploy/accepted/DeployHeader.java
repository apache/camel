package org.apache.camel.component.casper.consumer.sse.model.deploy.accepted;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SSE DeployAccepted event Header POJO
 * @author p35862
 *
 */
public class DeployHeader {
	private String account;
	private String timestamp;
	private String ttl;
	private float gasPrice;
	private String bodyHash;
	ArrayList<String> dependencies = new ArrayList<String>();
	private String chainName;

	@JsonProperty("gas_price")
	public float getGasPrice() {
		return gasPrice;
	}

	public void setGasPrice(float gasPrice) {
		this.gasPrice = gasPrice;
	}

	@JsonProperty("body_hash")
	public String getBodyHash() {
		return bodyHash;
	}

	public void setBodyHash(String bodyHash) {
		this.bodyHash = bodyHash;
	}

	@JsonProperty("chain_name")
	public String getChainName() {
		return chainName;
	}

	public void setChainName(String chainName) {
		this.chainName = chainName;
	}

	public String getAccount() {
		return account;
	}

	public String getTimestamp() {
		return timestamp;
	}

	public String getTtl() {
		return ttl;
	}

	public void setAccount(String account) {
		this.account = account;
	}

	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}

	public void setTtl(String ttl) {
		this.ttl = ttl;
	}

	public ArrayList<String> getDependencies() {
		return dependencies;
	}

	public void setDependencies(ArrayList<String> dependencies) {
		this.dependencies = dependencies;
	}

	public DeployHeader(String account, String timestamp, String ttl, float gasPrice, String bodyHash,
			ArrayList<String> dependencies, String chainName) {
		super();
		this.account = account;
		this.timestamp = timestamp;
		this.ttl = ttl;
		this.gasPrice = gasPrice;
		this.bodyHash = bodyHash;
		this.dependencies = dependencies;
		this.chainName = chainName;
	}

}