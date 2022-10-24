package org.apache.camel.component.casper.consumer.sse.model.deploy.processed;

import java.util.ArrayList;

/**
 * SSE DeployProcessed Event Effect POJO
 * @author p35862
 *
 */
public class Effect {
	ArrayList<String> operations = new ArrayList<String>();
	ArrayList<String> transforms = new ArrayList<String>();

	public ArrayList<String> getOperations() {
		return operations;
	}

	public void setOperations(ArrayList<String> operations) {
		this.operations = operations;
	}

	public ArrayList<String> getTransforms() {
		return transforms;
	}

	public void setTransforms(ArrayList<String> transforms) {
		this.transforms = transforms;
	}

	public Effect(ArrayList<String> operations, ArrayList<String> transforms) {
		super();
		this.operations = operations;
		this.transforms = transforms;
	}

}
