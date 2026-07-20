{
    "version": "{{projectVersion}}",
    "description": "{{projectDescription}}",
    "homepage": "{{projectLinkHomepage}}",
    "license": "{{projectLicense}}",
    "url": "{{distributionUrl}}",
    "hash": "sha256:{{distributionChecksumSha256}}",
    "extract_dir": "{{distributionArtifactRootEntryName}}",
    "bin": "bin\\{{distributionExecutableWindows}}",
    "post_install": "foreach ($native_exe in @('camel-x64.exe', 'camel-arm64.exe')) { $native_path = Join-Path \"$dir\\bin\" $native_exe; if (Test-Path -LiteralPath $native_path) { Remove-Item -LiteralPath $native_path -Force -ErrorAction Stop } }",
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
        "extract_dir": "camel-launcher-$version",
        "hash": {
            "url": "$url.sha1"
        }
    }
}
