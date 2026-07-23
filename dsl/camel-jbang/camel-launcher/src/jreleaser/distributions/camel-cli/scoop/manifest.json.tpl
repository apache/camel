{
    "version": "{{projectVersion}}",
    "description": "{{projectDescription}}",
    "homepage": "{{{projectLinkHomepage}}}",
    "license": "{{projectLicense}}",
    "url": "{{{distributionUrl}}}",
    "hash": "sha256:{{distributionChecksumSha256}}",
    "extract_dir": "{{distributionArtifactRootEntryName}}",
    "bin": "bin\\{{distributionExecutableWindows}}",
    "post_install": [
        "Remove-Item \"$dir\\bin\\camel-x64.exe\" -ErrorAction SilentlyContinue",
        "Remove-Item \"$dir\\bin\\camel-arm64.exe\" -ErrorAction SilentlyContinue"
    ],
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
