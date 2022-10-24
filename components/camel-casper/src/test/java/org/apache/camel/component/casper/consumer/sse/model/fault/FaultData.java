package org.apache.camel.component.casper.consumer.sse.model.fault;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SSE Fault event Data POJO
 * @author p35862
 *
 */
public class FaultData {

	private Fault fault;

	@JsonProperty("Fault")
	public Fault getFault() {
		return fault;
	}

	public void setFault(Fault fault) {
		this.fault = fault;
	}

	public FaultData(Fault fault) {
		super();
		this.fault = fault;
	}
	
}
