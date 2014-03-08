## ------------------------------------------------------------------------
## Licensed to the Apache Software Foundation (ASF) under one or more
## contributor license agreements.  See the NOTICE file distributed with
## this work for additional information regarding copyright ownership.
## The ASF licenses this file to You under the Apache License, Version 2.0
## (the "License"); you may not use this file except in compliance with
## the License.  You may obtain a copy of the License at
##
## http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software
## distributed under the License is distributed on an "AS IS" BASIS,
## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
## See the License for the specific language governing permissions and
## limitations under the License.
## ------------------------------------------------------------------------
require "java"

module Camel

  java_import "org.apache.camel.impl.DefaultCamelContext"
  java_import "org.apache.camel.impl.ExpressionSupport"
  java_import "org.apache.camel.model.RouteDefinition"
  java_import "org.apache.camel.ruby.ScriptRouteBuilder"

  class RubyRouteBuilder < ScriptRouteBuilder

    # def from(uri)
    #   @node = super.from(uri)
    #   @node
    # end
    # 
    # def to(uri)
    #   @node = @node.to(uri)
    #   @node
    # end
    # 
    
    
    def filter(params={}, &predicate)
      e = expression(&predicate)
      answer = getNode().filter(e)
      setNode(answer)
      return answer
    end
    
    def aaaafilter2(predicate, &block)
      puts "Called filter2 with #{predicate}"
      node = getNode()
      setNode(node.filter(predicate))
      block.call(getNode())
      setNode(node)
    end
    
    def expression(&block)
      e = BlockExpression.new
      e.value(&block)
    end
  end
  
  class BlockExpression < ExpressionSupport

    def value(&block)
      @block = block
      self
    end
    
    def evaluate(exchange)
      @block.call(exchange)
    end

    # TODO type parameter should be supported in block call
    def evaluate(exchange, type)
      @block.call(exchange)
    end

    def assertionFailureMessage(text)
      text
    end
    
    def to_s
      @block.to_s
    end
  end
end

