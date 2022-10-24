package org.apache.camel.component.casper.consumer.sse.model.deploy.expired;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SSE DeployExpired event POJO
 * 
 * @author p35862
 *
 */
public class DeployExpired {
	private String deployHash;

	@JsonProperty("deply_hash")
	public String getDeployHash() {
		return deployHash;
	}

	public void setDeployHash(String deployHash) {
		this.deployHash = deployHash;
	}

	public DeployExpired(String deployHash) {
		this.deployHash = deployHash;
	}

}
