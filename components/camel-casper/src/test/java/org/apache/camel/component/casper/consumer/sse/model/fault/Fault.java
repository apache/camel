package org.apache.camel.component.casper.consumer.sse.model.fault;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SSE Fault event POJO
 * 
 * @param era_id
 * @param timestamp
 * @param public_key
 */

public class Fault {

	private int eraId;
	private String timestamp;
	private String publicKey;

	@JsonProperty("era_id")
	public float getEraId() {
		return eraId;
	}

	public void setEraId(int eraId) {
		this.eraId = eraId;
	}

	public String getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}

	@JsonProperty("public_key")
	public String getPublicKey() {
		return publicKey;
	}

	public void setPublicKey(String publicKey) {
		this.publicKey = publicKey;
	}

	public Fault(int eraId, String timestamp, String publicKey) {

		this.eraId = eraId;
		this.timestamp = timestamp;
		this.publicKey = publicKey;
	}

}
