package org.apache.camel.component.spring.ws.filter.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.activation.DataHandler;
import javax.xml.namespace.QName;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.spring.ws.SpringWebserviceConstants;
import org.apache.camel.component.spring.ws.filter.MessageFilter;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapMessage;

/**
 * This class populates a SOAP header and attachments in the WebServiceMessage instance.
 * 
 * @author andrej@chocolatejar.eu
 * 
 */
public class BasicMessageFilter implements MessageFilter {
	private static final String LOWERCASE_BREADCRUMB_ID = "breadcrumbid";

	@Override
	public void filterProducer(Exchange exchange, WebServiceMessage response) {
		if (exchange != null) {
			processHeaderAndAttachments(exchange.getIn(), response);
		}
	}

	@Override
	public void filterConsumer(Exchange exchange, WebServiceMessage response) {
		if (exchange != null) {
			processHeaderAndAttachments(exchange.getOut(), response);
		}
	}

	/**
	 * If applicable this method adds a SOAP headers and attachments.
	 * 
	 * @param inOrOut
	 * @param response
	 */
	protected void processHeaderAndAttachments(Message inOrOut,
			WebServiceMessage response) {

		if (response instanceof SoapMessage) {
			SoapMessage soapMessage = (SoapMessage) response;
			processSoapHeader(inOrOut, soapMessage);
			doProcessSoapAttachments(inOrOut, soapMessage);
		}
	}

	/**
	  * If applicable this method adds a SOAP header.
	 * 
	 * @param inOrOut
	 * @param soapMessage
	 */
	protected void processSoapHeader(Message inOrOut, SoapMessage soapMessage) {
		boolean isHeaderAvailable = inOrOut != null
				&& inOrOut.getHeaders() != null
				&& !inOrOut.getHeaders().isEmpty();

		if (isHeaderAvailable) {
			doProcessSoapHeader(inOrOut, soapMessage);
		}
	}

	/**
	 * The SOAP header is populated from exchange.getOut().getHeaders() if this
	 * class is used by the consumer or exchange.getIn().getHeaders() if this
	 * class is used by the producer.
	 * 
	 * If .getHeaders() contains under a certain key a value with the QName
	 * object, it is directly added as a new header element. If it contains only
	 * a String value, it is transformed into a header attribute.
	 * 
	 * Following headers are excluded: {@code LOWERCASE_BREADCRUMB_ID}
	 * 
	 * @see SpringWebserviceConstants.SPRING_WS_SOAP_ACTION, @see
	 *      SpringWebserviceConstants.SPRING_WS_ADDRESSING_ACTION), @see
	 *      SpringWebserviceConstants.SPRING_WS_ENDPOINT_URI
	 * 
	 * This the convinient method for overriding.
	 * @param inOrOut
	 * @param soapMessage
	 */
	protected void doProcessSoapHeader(Message inOrOut, SoapMessage soapMessage) {
		SoapHeader soapHeader = soapMessage.getSoapHeader();

		Map<String, Object> headers = inOrOut.getHeaders();

		HashSet<String> headerKeySet = new HashSet<String>(headers.keySet());

		headerKeySet.remove(SpringWebserviceConstants.SPRING_WS_SOAP_ACTION.toLowerCase());
		headerKeySet.remove(SpringWebserviceConstants.SPRING_WS_ADDRESSING_ACTION.toLowerCase());
		headerKeySet.remove(SpringWebserviceConstants.SPRING_WS_ENDPOINT_URI.toLowerCase());

		headerKeySet.remove(LOWERCASE_BREADCRUMB_ID);

		for (String name : headerKeySet) {
			Object value = headers.get(name);

			if (value instanceof QName) {
				soapHeader.addHeaderElement((QName) value);
			} else {
				if (value instanceof String) {
					soapHeader.addAttribute(new QName(name), value + "");
				}
			}
		}
	}

	/**
	 * Populate SOAP attachments from in or out exchange message. This the convenient method for overriding.
	 * 
	 * @param inOrOut
	 * @param response
	 */
	protected void doProcessSoapAttachments(Message inOrOut, SoapMessage response) {
		Map<String, DataHandler> attachments = inOrOut.getAttachments();

		Set<String> keySet = new HashSet<String>(attachments.keySet());
		for (String key : keySet) {
			response.addAttachment(key, attachments.get(key));
		}
	}

}
