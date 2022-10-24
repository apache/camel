package org.apache.camel.component.casper.consumer.sse.model.deploy.processed;

/**
 * SSE DeployProcessed Event ExecutionResult POJO
 * 
 * @author p35862
 *
 */

public class ExecutionResult {
	Failure failure;

	public Failure getFailure() {
		return failure;
	}

	public void setFailure(Failure failure) {
		this.failure = failure;
	}

	public ExecutionResult(Failure failure) {

		this.failure = failure;
	}

}
