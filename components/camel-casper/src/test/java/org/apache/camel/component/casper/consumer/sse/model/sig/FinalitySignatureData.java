package org.apache.camel.component.casper.consumer.sse.model.sig;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SSE FinalitySignature Event Data POJO
 * 
 * @author p35862
 *
 */
public class FinalitySignatureData {
	FinalitySignature finalitySignature;

	@JsonProperty("FinalitySignature")
	public FinalitySignature getFinalitySignature() {
		return finalitySignature;
	}

	public void setFinalitySignature(FinalitySignature finalitySignature) {
		this.finalitySignature = finalitySignature;
	}

	public FinalitySignatureData(FinalitySignature finalitySignature) {

		this.finalitySignature = finalitySignature;
	}

}
