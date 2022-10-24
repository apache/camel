package org.apache.camel.component.casper.consumer.sse.model.deploy.processed;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeployProcessedData {

	DeployProcessed DeployProcessedObject;


	 // Getter Methods 
	 @JsonProperty("DeployProcessed")
	 public DeployProcessed getDeployProcessed() {
	  return DeployProcessedObject;
	 }

	 // Setter Methods 

	 public void setDeployProcessed(DeployProcessed DeployProcessedObject) {
	  this.DeployProcessedObject = DeployProcessedObject;
	 }

	public DeployProcessedData(DeployProcessed deployProcessedObject) {
		super();
		DeployProcessedObject = deployProcessedObject;
	}


	 

}
	
	
	

