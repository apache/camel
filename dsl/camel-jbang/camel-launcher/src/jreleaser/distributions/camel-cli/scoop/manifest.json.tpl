{
    "version": "{{projectVersion}}",
    "description": "{{projectDescription}}",
    "homepage": "{{projectLinkHomepage}}",
    "license": "{{projectLicense}}",
    "url": "{{distributionUrl}}",
    "hash": "sha256:{{distributionChecksumSha256}}",
    "extract_dir": "{{distributionArtifactRootEntryName}}",
    "bin": "bin\\{{distributionExecutableWindows}}",
    "post_install": [
        "Remove-Item \"$dir\\bin\\camel-x64.exe\" -Force -ErrorAction SilentlyContinue",
        "Remove-Item \"$dir\\bin\\camel-arm64.exe\" -Force -ErrorAction SilentlyContinue"
    ],
    "suggest": {
        "JDK": [
            "java/oraclejdk",
            "java/openjdk"
        ]
    },
    "checkver": {
        "url": "{{scoopCheckverUrl}}",
        "re": "v([\\d.]+).zip"
    },
    "autoupdate": {
        "url": "{{scoopAutoupdateUrl}}",
        "extract_dir": "{{scoopAutoupdateExtractDir}}",
        "hash": {
            "url": "$url.sha256"
        }
    }
}
