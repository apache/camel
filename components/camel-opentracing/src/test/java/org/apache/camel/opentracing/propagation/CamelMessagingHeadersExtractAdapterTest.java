package org.apache.camel.opentracing.propagation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import static org.apache.camel.opentracing.propagation.CamelMessagingHeadersInjectAdapter.JMS_DASH;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

public class CamelMessagingHeadersExtractAdapterTest {
	
	private Map<String,Object> map;
	
	@Before
	public void before() {
	    map = new HashMap<String,Object>();
	}
	
	@Test
	public void noProperties() {
		CamelMessagingHeadersExtractAdapter adapter = new CamelMessagingHeadersExtractAdapter(map,true);
		Iterator<Map.Entry<String, String>> iterator = adapter.iterator();
		assertFalse(iterator.hasNext());
	}

	@Test
	public void oneProperty() {
		map.put("key", "value");
		CamelMessagingHeadersExtractAdapter adapter = new CamelMessagingHeadersExtractAdapter(map,true);
		Iterator<Map.Entry<String, String>> iterator = adapter.iterator();
		Map.Entry<String, String> entry = iterator.next();
		assertEquals("key", entry.getKey());
		assertEquals("value", entry.getValue());
	}

	@Test
	public void propertyWithDash() {
		map.put(JMS_DASH + "key" + JMS_DASH + "1" + JMS_DASH, "value1");
		CamelMessagingHeadersExtractAdapter adapter = new CamelMessagingHeadersExtractAdapter(map,true);
		Iterator<Map.Entry<String, String>> iterator = adapter.iterator();
		Map.Entry<String, String> entry = iterator.next();
		assertEquals("-key-1-", entry.getKey());
		assertEquals("value1", entry.getValue());
	}

	@Test
	public void propertyWithoutDashEncoding() {
		map.put(JMS_DASH + "key" + JMS_DASH + "1" + JMS_DASH, "value1");
		CamelMessagingHeadersExtractAdapter adapter = new CamelMessagingHeadersExtractAdapter(map,false);
		Iterator<Map.Entry<String, String>> iterator = adapter.iterator();
		Map.Entry<String, String> entry = iterator.next();
		assertEquals(JMS_DASH + "key" + JMS_DASH + "1" + JMS_DASH, entry.getKey());
	}
}
