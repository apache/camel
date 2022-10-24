package org.apache.camel.component.casper.consumer.sse.model.deploy.accepted;

/**
 * SSE DeployAccepted event Session POJO
 * 
 * @author p35862
 *
 */

public class Session {
	Transfer transfer;

	public Session(Transfer transfer) {

		this.transfer = transfer;
	}

	public Transfer getTransfer() {
		return transfer;
	}

	public void setTransfer(Transfer transfer) {
		this.transfer = transfer;
	}

}