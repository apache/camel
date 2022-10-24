package org.apache.camel.component.casper.consumer.sse.model.deploy.accepted;

import java.util.ArrayList;

/**
 * SSE DeployAccepted event Transfer POJO
 * 
 * @author p35862
 *
 */
public class Transfer {
	ArrayList<String> args = new ArrayList<String>();

	public Transfer(ArrayList<String> args) {
		this.args = args;
	}

	public ArrayList<String> getArgs() {
		return args;
	}

	public void setArgs(ArrayList<String> args) {
		this.args = args;
	}
}
