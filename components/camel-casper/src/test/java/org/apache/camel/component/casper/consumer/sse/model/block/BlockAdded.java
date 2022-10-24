package org.apache.camel.component.casper.consumer.sse.model.block;

/**
 * SSE BlockAdded POJO
 * 
 * @author mabahma
 *
 */

public class BlockAdded {
	private String hash;
	private Block block;

	public BlockAdded(String hash, Block block) {
		super();
		this.hash = hash;
		this.block = block;
	}

	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}

	public Block getBlock() {
		return block;
	}

	public void setBlock(Block block) {
		this.block = block;
	}
}