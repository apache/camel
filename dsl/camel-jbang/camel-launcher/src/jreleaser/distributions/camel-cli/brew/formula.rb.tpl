#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# {{jreleaserCreationStamp}}
{{#brewRequireRelative}}
require_relative "{{.}}"
{{/brewRequireRelative}}

class {{brewFormulaName}} < Formula
  desc "{{projectDescription}}"
  homepage "{{{projectLinkHomepage}}}"
  url "{{{distributionUrl}}}"{{#brewDownloadStrategy}}, :using => {{.}}{{/brewDownloadStrategy}}
  version "{{projectVersion}}"
  sha256 "{{distributionChecksumSha256}}"
  license "{{projectLicense}}"
{{#brewVersionedFormula}}

  keg_only :versioned_formula

  deprecate! date: "{{brewDeprecateDate}}", because: :unsupported
  disable! date: "{{brewDisableDate}}", because: :unsupported
{{/brewVersionedFormula}}
{{#brewHasLivecheck}}

  livecheck do
    {{#brewLivecheck}}
    {{.}}
    {{/brewLivecheck}}
  end
{{/brewHasLivecheck}}
{{#brewDependencies}}

  depends_on {{.}}
{{/brewDependencies}}

  def install
    libexec.install Dir["*"]
    (bin/"{{distributionExecutableName}}").write_env_script libexec/"bin/camel.sh",
      Language::Java.overridable_java_home_env("21")
  end

  test do
    output = shell_output("#{bin}/{{distributionExecutableName}} --version")
    assert_match "{{projectVersion}}", output
  end
end
