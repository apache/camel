package org.apache.camel.component.dns.policy;

import javax.naming.directory.Attributes;
import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import java.net.NetworkInterface;
import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.List;
import java.util.ArrayList;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* 
 * Check if a hostname resolves to a specified cname or an ip
 */

public class DnsActivation {
    private static final transient Logger logger = LoggerFactory.getLogger(DnsActivation.class);

	private final static String[] DNS_TYPES_CNAME = {"CNAME"}; 

	private String hostname;
	private List<String> resolvesTo = new ArrayList<String>();

	public DnsActivation() throws Exception {
	}

	public DnsActivation(String hostname, List<String> resolvesTo) throws Exception {
		this.hostname = hostname;
		this.resolvesTo.addAll(resolvesTo);
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public void setResolvesTo(List<String> resolvesTo) {
		this.resolvesTo.addAll(resolvesTo);
	}

	public void setResolvesTo(String resolvesTo) {
		this.resolvesTo.add(resolvesTo);
	}

	public boolean isActive() {
		if(resolvesTo.isEmpty()) {
			try {
				resolvesTo.addAll(getLocalIps());
			} catch(Exception e) {
				logger.warn("Failed to get local ips and resolvesTo not specified. Identifying as inactive.", e);
				return false;
			}
		}

		logger.debug("Resolving "+hostname);
		ArrayList<String> hostnames = new ArrayList<String>();
		hostnames.add(hostname);

		ArrayList<String> resolved = new ArrayList<String>();
		while(!hostnames.isEmpty()) {
			NamingEnumeration attributeEnumeration = null;
			try {
				String hostname = hostnames.remove(0);
				InetAddress inetAddress = InetAddress.getByName(hostname);
				InitialDirContext initialDirContext = new InitialDirContext();
				Attributes attributes = initialDirContext.getAttributes("dns:/" + inetAddress.getHostName(), DNS_TYPES_CNAME);
				attributeEnumeration = attributes.getAll();
				while(attributeEnumeration.hasMore()) {
					Attribute attribute = (Attribute)attributeEnumeration.next();
					String id = attribute.getID();
					String value = (String)attribute.get();
					if(resolvesTo.contains(value)) {
						logger.debug(id+" = " + value + " matched. Identifying as active.");
						return true;
					}
					logger.debug(id+" = " + value);
					if(id.equals("CNAME") && !resolved.contains(value)) {
						hostnames.add(value);
					}
					resolved.add(value);
				}
			} catch(Exception e) {
				logger.warn(hostname, e);
			} finally {
				if(attributeEnumeration != null) {
					try {
						attributeEnumeration.close();
					} catch(Exception e) {
						logger.warn("Failed to close attributeEnumeration. Memory leak possible.", e);
					}
					attributeEnumeration = null;
				}
			}
		}
		return false;
	}

	private List<String> getLocalIps() throws Exception {
		List<String> localIps = new ArrayList<String>();

		Enumeration<NetworkInterface> networkInterfacesEnumeration = NetworkInterface.getNetworkInterfaces();
		while(networkInterfacesEnumeration.hasMoreElements()) {
			NetworkInterface networkInterface = networkInterfacesEnumeration.nextElement();

			Enumeration<InetAddress> inetAddressesEnumeration = networkInterface.getInetAddresses();
			while (inetAddressesEnumeration.hasMoreElements()) {
				InetAddress inetAddress = inetAddressesEnumeration.nextElement();
				String ip = inetAddress.getHostAddress();
				if(ip.startsWith("127.")) {
					continue;
				}
				logger.debug("Local ip: "+ip);
				localIps.add(ip);
			}
		}
		return localIps;
	}
}

