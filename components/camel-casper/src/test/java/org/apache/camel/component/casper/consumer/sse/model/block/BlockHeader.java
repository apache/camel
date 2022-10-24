package org.apache.camel.component.casper.consumer.sse.model.block;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SSE BlockAdded Event BlockHeader POJO
 * @author p35862
 *
 */
public class BlockHeader {
	private String parentHash;
	private String stateRootHash;
	private String bodyHash;
	private boolean randomBit;
	private String accumulatedSseed;
	private String eraEnd;
	private String timestamp;
	private float eraId;
	private float height;
	private String protocolVersion;

	@JsonProperty("parent_hash")
	public String getParentHash() {
		return parentHash;
	}

	public void setParentHash(String parentHash) {
		this.parentHash = parentHash;
	}
	@JsonProperty("state_root_hash")
	public String getStateRootHash() {
		return stateRootHash;
	}

	public void setStateRootHash(String stateRootHash) {
		this.stateRootHash = stateRootHash;
	}
	@JsonProperty("body_hash")
	public String getBodyHash() {
		return bodyHash;
	}

	public void setBodyHash(String bodyHash) {
		this.bodyHash = bodyHash;
	}

	@JsonProperty("random_bit")
	public boolean isRandomBit() {
		return randomBit;
	}

	public void setRandomBit(boolean randomBit) {
		this.randomBit = randomBit;
	}

	@JsonProperty("accumulated_seed")
	public String getAccumulatedSseed() {
		return accumulatedSseed;
	}

	public void setAccumulatedSseed(String accumulatedSseed) {
		this.accumulatedSseed = accumulatedSseed;
	}

	@JsonProperty("era_end")
	public String getEraEnd() {
		return eraEnd;
	}

	public void setEraEnd(String eraEnd) {
		this.eraEnd = eraEnd;
	}

	@JsonProperty("timestamp")
	public String getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}

	@JsonProperty("era_id")
	public float getEraId() {
		return eraId;
	}

	public void setEraId(float eraId) {
		this.eraId = eraId;
	}

	@JsonProperty("height")
	public float getHeight() {
		return height;
	}

	public void setHeight(float height) {
		this.height = height;
	}

	@JsonProperty("protocol_version")
	public String getProtocolVersion() {
		return protocolVersion;
	}

	public void setProtocolVersion(String protocolVersion) {
		this.protocolVersion = protocolVersion;
	}

	public BlockHeader(String parentHash, String stateRootHash, String bodyHash, boolean randomBit,
			String accumulatedSseed, String eraEnd, String timestamp, float eraId, float height,
			String protocolVersion) {
		super();
		this.parentHash = parentHash;
		this.stateRootHash = stateRootHash;
		this.bodyHash = bodyHash;
		this.randomBit = randomBit;
		this.accumulatedSseed = accumulatedSseed;
		this.eraEnd = eraEnd;
		this.timestamp = timestamp;
		this.eraId = eraId;
		this.height = height;
		this.protocolVersion = protocolVersion;
	}

}