package org.apache.camel.component.dns.policy;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.util.ServiceHelper;

import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.ExceptionHandler;

import org.apache.camel.impl.LoggingExceptionHandler;
import org.apache.camel.support.RoutePolicySupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DnsActivationPolicy extends RoutePolicySupport {
	private final static Logger logger = LoggerFactory.getLogger(DnsActivationPolicy.class);

	private ExceptionHandler exceptionHandler;
	
	private DnsActivation dnsActivation;
	private long ttl;

	private Map<String,Route> routes = new ConcurrentHashMap<String, Route>();
	private Timer timer;

	public DnsActivationPolicy() throws Exception
	{
		dnsActivation = new DnsActivation();
	}

	public void onInit(Route route)
	{
		logger.debug("onInit "+route.getId());
		routes.put(route.getId(), route);
	}

	public void onRemove(Route route)
	{
		logger.debug("onRemove "+route.getId());
		// noop
	}

	@Override
	public void onStart(Route route)
	{
		logger.debug("onStart "+route.getId());
		// noop
	}

	@Override
	public void onStop(Route route)
	{
		logger.debug("onStop "+route.getId());
		// noop
	}

	@Override
	public void onSuspend(Route route)
	{
		logger.debug("onSuspend "+route.getId());
		// noop
	}

	@Override
	public void onResume(Route route)
	{
		logger.debug("onResume "+route.getId());
		// noop
	}

	public void onExchangeBegin(Route route, Exchange exchange)
	{
		logger.debug("onExchange start "+route.getId()+"/"+exchange.getExchangeId());
		// noop
	}

	public void onExchangeDone(Route route, Exchange exchange)
	{
		logger.debug("onExchange end "+route.getId()+"/"+exchange.getExchangeId());
		// noop
	}

	@Override
	protected void doStart() throws Exception
	{
		logger.debug("doStart");
		timer = new Timer();
		timer.schedule(new DnsActivationTask(), 0, ttl);
		// noop
	}

	@Override
	protected void doStop() throws Exception
	{
		logger.debug("doStop");
		if(timer != null)
		{
			timer.cancel();
			timer = null;
		}

		// noop
	}

	public ExceptionHandler getExceptionHandler() {
		if (exceptionHandler == null) {
			exceptionHandler = new LoggingExceptionHandler(getClass());
		}
		return exceptionHandler;
	}

	public void setExceptionHandler(ExceptionHandler exceptionHandler) {
		this.exceptionHandler = exceptionHandler;
	}

	public void setHostname(String hostname) {
		dnsActivation.setHostname(hostname);
	}

	public void setResolvesTo(List<String> resolvesTo) {
		dnsActivation.setResolvesTo(resolvesTo);
	}

	public void setResolvesTo(String resolvesTo) {
		dnsActivation.setResolvesTo(resolvesTo);
	}

	public void setTtl(String ttl) {
		this.ttl = Long.parseLong(ttl);
	}

	private void startRouteImpl(Route route) throws Exception {
		ServiceStatus routeStatus = route.getRouteContext().getCamelContext().getRouteStatus(route.getId());

		if(routeStatus == ServiceStatus.Stopped) {
			logger.info("Starting "+route.getId());
			startRoute(route);
		} else if(routeStatus == ServiceStatus.Suspended) {
			logger.info("Resuming "+route.getId());
			startConsumer(route.getConsumer());
		} else {
			logger.debug("Nothing to do "+route.getId()+" is "+routeStatus);
		}
	}

	private void startRoutes()
	{
			for(String routeId : routes.keySet()) {
				try {
					Route route = routes.get(routeId);
					startRouteImpl(route);
				} catch(Exception e) {
					logger.warn(routeId, e);
				}
			}
	}

	private void stopRouteImpl(Route route) throws Exception {
		ServiceStatus routeStatus = route.getRouteContext().getCamelContext().getRouteStatus(route.getId());

		if(routeStatus == ServiceStatus.Started) {
			logger.info("Stopping "+route.getId());
			stopRoute(route);
		} else {
			logger.debug("Nothing to do "+route.getId()+" is "+routeStatus);
		}
	}

	private void stopRoutes()
	{
			for(String routeId : routes.keySet()) {
				try {
					Route route = routes.get(routeId);
					stopRouteImpl(route);
				} catch(Exception e) {
					logger.warn(routeId, e);
				}
			}
	}

 	class DnsActivationTask extends TimerTask {
		public void run() {
			try {
				if(dnsActivation.isActive()) {
					startRoutes();
				} else {
					stopRoutes();
				}
			}
			catch(Exception e) {
				logger.warn("DnsActivation TimerTask failed", e);
			}
		}
	}
}


