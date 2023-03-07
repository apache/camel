package org.apache.camel.dsl.jbang.core.commands;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;

import picocli.CommandLine;

@CommandLine.Command(name = "set",
		description = "Set user config value")
public class ConfigSet extends CamelCommand {

	@CommandLine.Parameters(description = "Configuration parameter (ex. key=value)")
	private String configuration;

	public ConfigSet(CamelJBangMain main) {
		super(main);
	}

	@Override
	public Integer call() throws Exception {
		CommandLineHelper.createPropertyFile();

		if (configuration.split("=").length == 1) {
			System.out.println("Configuration parameter not in key=value form");

			return 1;
		}

		CommandLineHelper.loadProperties(properties -> {
			String key = configuration.substring(0, configuration.indexOf("="));
			String value = configuration.substring(configuration.indexOf("=") + 1, configuration.length());
			properties.put(key, value);
			CommandLineHelper.storeProperties(properties);
		});

		return 0;
	}
}
