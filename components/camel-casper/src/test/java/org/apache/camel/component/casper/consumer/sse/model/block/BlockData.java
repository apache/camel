package org.apache.camel.component.casper.consumer.sse.model.block;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SSE BlockAdded BlockData POJO
 * 
 * @author mabahma
 *
 */
public class BlockData {

	private BlockAdded blockAdded;

	public BlockData(BlockAdded blockAdded) {
		super();
		this.blockAdded = blockAdded;
	}

	@JsonProperty("BlockAdded")
	public BlockAdded getBlockAdded() {
		return blockAdded;
	}

	public void setBlockAdded(BlockAdded BlockAdded) {
		this.blockAdded = BlockAdded;
	}
}
