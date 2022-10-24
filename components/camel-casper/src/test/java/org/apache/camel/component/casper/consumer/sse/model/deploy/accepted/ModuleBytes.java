package org.apache.camel.component.casper.consumer.sse.model.deploy.accepted;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SSE DeployAccepted event ModuleBytes POJO
 * 
 * @author p35862
 *
 */
public class ModuleBytes {
	private String moduleBytes;
	ArrayList<String> args = new ArrayList<String>();

	public ModuleBytes(String moduleBytes, ArrayList<String> args) {
		super();
		this.moduleBytes = moduleBytes;
		this.args = args;
	}

	@JsonProperty("module_bytes")
	public String getModuleBytes() {
		return moduleBytes;
	}

	public void setModuleBytes(String moduleBytes) {
		this.moduleBytes = moduleBytes;
	}

	public ArrayList<String> getArgs() {
		return args;
	}

	public void setArgs(ArrayList<String> args) {
		this.args = args;
	}

}
