package org.apache.camel.component.casper.consumer.sse.model.sig;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SSE FinalitySignature Event POJO
 * 
 * @author p35862
 *
 */
public class FinalitySignature {
	private String blockHash;
	private int eraId;
	private String signature;
	private String publicKey;

	@JsonProperty("block_hash")
	public String getBlockHash() {
		return blockHash;
	}

	public void setBlockHash(String blockHash) {
		this.blockHash = blockHash;
	}

	@JsonProperty("era_id")
	public int getEraId() {
		return eraId;
	}

	public void setEraId(int eraId) {
		this.eraId = eraId;
	}

	@JsonProperty("")
	public String getSignature() {
		return signature;
	}

	public void setSignature(String signature) {
		this.signature = signature;
	}

	@JsonProperty("public_key")
	public String getPublicKey() {
		return publicKey;
	}

	public void setPublicKey(String publicKey) {
		this.publicKey = publicKey;
	}

	public FinalitySignature(String blockHash, int eraId, String signature, String publicKey) {
		super();
		this.blockHash = blockHash;
		this.eraId = eraId;
		this.signature = signature;
		this.publicKey = publicKey;
	}
}