package org.apache.camel.component.casper.consumer.sse.model.deploy.expired;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * * SSE DeployExpired event Data POJO
 * 
 * @author p35862
 *
 */
public class DeployExpiredData {
	DeployExpired deployExpired;

	@JsonProperty("DeployExpired")
	public DeployExpired getDeployExpired() {
		return deployExpired;
	}

	public void setDeployExpired(DeployExpired deployExpired) {
		this.deployExpired = deployExpired;
	}

	public DeployExpiredData(DeployExpired deployExpired) {
		super();
		this.deployExpired = deployExpired;
	}
}
