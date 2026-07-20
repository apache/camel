{
    "version": "{{projectVersion}}",
    "description": "{{projectDescription}}",
    "homepage": "{{{projectLinkHomepage}}}",
    "license": "{{projectLicense}}",
    "url": "{{{distributionUrl}}}",
    "hash": "sha256:{{distributionChecksumSha256}}",
    "extract_dir": "{{distributionArtifactRootEntryName}}",
    "bin": "bin\\{{distributionExecutableWindows}}",
    "suggest": {
        "JDK": [
            "java/oraclejdk",
            "java/openjdk"
        ]
    },
    "checkver": {
        "url": "https://repo1.maven.org/maven2/org/apache/camel/camel-launcher/maven-metadata.xml",
        "regex": "<release>([\\d.]+)</release>"
    },
    "autoupdate": {
        "url": "https://repo1.maven.org/maven2/org/apache/camel/camel-launcher/$version/camel-launcher-$version-bin.zip",
        "extract_dir": "camel-launcher-$version"
    }
}
