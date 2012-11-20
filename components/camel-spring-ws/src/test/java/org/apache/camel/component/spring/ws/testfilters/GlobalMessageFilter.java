package org.apache.camel.component.spring.ws.testfilters;

import javax.xml.namespace.QName;

import org.apache.camel.Message;
import org.apache.camel.component.spring.ws.filter.impl.BasicMessageFilter;
import org.springframework.ws.soap.SoapMessage;

public class GlobalMessageFilter extends BasicMessageFilter {

	/**
	 * Add a test marker so the test method is aware which filter we are using.
	 */
	@Override
	protected void doProcessSoapAttachments(Message inOrOut,
			SoapMessage response) {
		super.doProcessSoapAttachments(inOrOut, response);
		response.getEnvelope()
				.getHeader()
				.addHeaderElement(
						new QName("http://virtualCheck/", "globalFilter"));
	}
}
