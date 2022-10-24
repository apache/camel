package org.apache.camel.component.casper.consumer.sse.model.deploy.accepted;

/**
 * SSE DeployAccepted event Payment POJO
 * 
 * @author p35862
 *
 */
public class Payment {
	ModuleBytes moduleBytes;

	public ModuleBytes getModuleBytes() {
		return moduleBytes;
	}

	public void setModuleBytes(ModuleBytes moduleBytes) {
		this.moduleBytes = moduleBytes;
	}

	public ModuleBytes getModuleBytesObject() {
		return moduleBytes;
	}

	public Payment(ModuleBytes moduleBytes) {
		super();
		this.moduleBytes = moduleBytes;
	}
}