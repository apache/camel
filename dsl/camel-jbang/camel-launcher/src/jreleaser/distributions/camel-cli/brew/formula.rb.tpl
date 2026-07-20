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
{{/brewVersionedFormula}}
{{#brewHasLivecheck}}

  livecheck do
    {{#brewLivecheck}}
    {{.}}
    {{/brewLivecheck}}
  end
{{/brewHasLivecheck}}

  depends_on "openjdk"

  def install
    libexec.install Dir["*"]
    (bin/"{{distributionExecutableName}}").write_env_script libexec/"bin/camel.sh",
      CAMEL_FALLBACK_JAVA: "#{formula_opt_bin("openjdk")}/java"
  end

  def caveats
    <<~EOS
      Apache Camel CLI installs its own Homebrew OpenJDK dependency even if a
      compatible Java 17+ is already present. The launcher selects a Java runtime in
      this order: JAVACMD, JAVA_HOME/bin/java, the first java on PATH, then
      CAMEL_FALLBACK_JAVA (which this formula points at the Homebrew OpenJDK).

      Run "camel version" to verify the install.
{{#brewVersionedFormula}}

      #{name} is keg-only: it was not symlinked into #{HOMEBREW_PREFIX} because
      another version of this formula may also be installed. To use this version's
      "camel" first in your PATH, run:
        echo 'export PATH="#{opt_bin}:$PATH"' >> ~/.zshrc
{{/brewVersionedFormula}}
    EOS
  end

  test do
    output = shell_output("#{bin}/{{distributionExecutableName}} --version")
    assert_match "{{projectVersion}}", output
  end
end
