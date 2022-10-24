package org.apache.camel.component.casper.consumer.sse.model.deploy.processed;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SSE DeployProcessed Event POJO
 * 
 * @author p35862
 *
 */
public class DeployProcessed {
	private String deployHash;
	private String account;
	private String timestamp;
	private String ttl;
	private ArrayList<String> dependencies;
	private String blockHash;
	private ExecutionResult executionResult;

	public DeployProcessed(String deployHash, String account, String timestamp, String ttl,
			ArrayList<String> dependencies, String blockHash, ExecutionResult executionResult) {

		this.deployHash = deployHash;
		this.account = account;
		this.timestamp = timestamp;
		this.ttl = ttl;
		this.dependencies = dependencies;
		this.blockHash = blockHash;
		this.executionResult = executionResult;
	}

	@JsonProperty("deploy_hash")
	public String getDeployHash() {
		return deployHash;
	}

	public void setDeployHash(String deployHash) {
		this.deployHash = deployHash;
	}

	public String getAccount() {
		return account;
	}

	public void setAccount(String account) {
		this.account = account;
	}

	public String getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}

	public String getTtl() {
		return ttl;
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

	@JsonProperty("block_hash")
	public String getBlockHash() {
		return blockHash;
	}

	public void setBlockHash(String blockHash) {
		this.blockHash = blockHash;
	}

	public ExecutionResult getExecutionResult() {
		return executionResult;
	}

	public void setExecutionResult(ExecutionResult executionResult) {
		this.executionResult = executionResult;
	}

}