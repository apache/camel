package org.apache.camel.component.casper.consumer.sse.model.block;

import java.util.ArrayList;

/**
 * SSE BlockAdded event Block POJO
 * 
 * @author mabahma
 *
 */
public class Block {
	private String hash;
	private BlockHeader header;
	private Body body;
	private ArrayList<String> proofs;

	public Block(String hash, BlockHeader header, Body body, ArrayList<String> proofs) {
		this.hash = hash;
		this.header = header;
		this.body = body;
		this.proofs = proofs;
	}

	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}

	public BlockHeader getHeader() {
		return header;
	}

	public void setHeader(BlockHeader header) {
		this.header = header;
	}

	public Body getBody() {
		return body;
	}

	public void setBody(Body body) {
		this.body = body;
	}

	public ArrayList<String> getProofs() {
		return proofs;
	}

	public void setProofs(ArrayList<String> proofs) {
		this.proofs = proofs;
	}

}