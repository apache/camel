package org.apache.camel.component.casper.consumer.sse.model.deploy.accepted;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SSE DeployAccepted Data POJO
 * 
 * @author p35862
 *
 */
public class DeployAcceptedData {

	DeployAccepted deployAccepted;

	@JsonProperty("DeployAccepted")
	public DeployAccepted getDeployAccepted() {
		return deployAccepted;
	}

	public void setDeployAccepted(DeployAccepted deployAccepted) {
		this.deployAccepted = deployAccepted;
	}

	public DeployAcceptedData(DeployAccepted deployAccepted) {

		this.deployAccepted = deployAccepted;
	}

}
