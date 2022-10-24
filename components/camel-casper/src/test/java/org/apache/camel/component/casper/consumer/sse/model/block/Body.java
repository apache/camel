package org.apache.camel.component.casper.consumer.sse.model.block;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SSE BlockAdded event Body POJO
 * @author p35862
 *
 */
public class Body {
	private String proposer;
	ArrayList<String> deployHashes = new ArrayList<String>();
	ArrayList<String> transferHhashes = new ArrayList<String>();

	public Body(String proposer, ArrayList<String> deployHashes, ArrayList<String> transferHhashes) {
		super();
		this.proposer = proposer;
		this.deployHashes = deployHashes;
		this.transferHhashes = transferHhashes;
	}

	@JsonProperty("deploy_hashes")
	public ArrayList<String> getDeployHashes() {
		return deployHashes;
	}

	public void setDeployHashes(ArrayList<String> deployHashes) {
		this.deployHashes = deployHashes;
	}

	@JsonProperty("transfer_hashes")
	public ArrayList<String> getTransferHhashes() {
		return transferHhashes;
	}

	public void setTransferHhashes(ArrayList<String> transferHhashes) {
		this.transferHhashes = transferHhashes;
	}

	@JsonProperty("proposer")
	public String getProposer() {
		return proposer;
	}

	public void setProposer(String proposer) {
		this.proposer = proposer;
	}
}