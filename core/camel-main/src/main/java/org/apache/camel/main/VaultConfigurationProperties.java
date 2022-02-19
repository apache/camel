package org.apache.camel.main;

import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.vault.VaultConfiguration;

public class VaultConfigurationProperties extends VaultConfiguration implements BootstrapCloseable {

    private MainConfigurationProperties parent;
    private AwsVaultConfigurationProperties aws;

    public VaultConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
        if (aws != null) {
            aws.close();
        }
    }

    // getter and setters
    // --------------------------------------------------------------

    // these are inherited from the parent class

    // fluent builders
    // --------------------------------------------------------------

    @Override
    public AwsVaultConfigurationProperties aws() {
        if (aws == null) {
            aws = new AwsVaultConfigurationProperties(parent);
        }
        return aws;
    }

}
