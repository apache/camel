package org.apache.camel.component.casper.consumer.sse.model.deploy.accepted;

import java.util.ArrayList;

/**
 * SSE DeployAccepted POJO
 * @author p35862
 *
 */
public class DeployAccepted {
	private String hash;
	DeployHeader header;
	Payment payment;
	Session session;
	ArrayList<String> approvals = new ArrayList<String>();

	public DeployAccepted(String hash, DeployHeader header, Payment payment, Session session,
			ArrayList<String> approvals) {

		this.hash = hash;
		this.header = header;
		this.payment = payment;
		this.session = session;
		this.approvals = approvals;
	}

	public DeployHeader getHeader() {
		return header;
	}

	public void setHeader(DeployHeader header) {
		this.header = header;
	}

	public Payment getPayment() {
		return payment;
	}

	public void setPayment(Payment payment) {
		this.payment = payment;
	}

	public Session getSession() {
		return session;
	}

	public void setSession(Session session) {
		this.session = session;
	}

	public ArrayList<String> getApprovals() {
		return approvals;
	}

	public void setApprovals(ArrayList<String> approvals) {
		this.approvals = approvals;
	}

	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}

}
