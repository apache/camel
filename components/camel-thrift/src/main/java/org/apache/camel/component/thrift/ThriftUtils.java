/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.thrift;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.apache.camel.CamelContext;
import org.apache.camel.component.thrift.client.AsyncClientMethodCallback;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReflectionHelper;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.protocol.TSimpleJSONProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TZlibTransport;

/**
 * ThriftUtils helpers are working with dynamic methods via Camel and Java
 * reflection utilities
 */
public final class ThriftUtils {

    private ThriftUtils() {
    }

    public static String extractServiceName(String service) {
        return service.substring(service.lastIndexOf(".") + 1);
    }

    public static String extractServicePackage(String service) {
        return service.substring(0, service.lastIndexOf("."));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object constructClientInstance(String packageName, String serviceName, TTransport transport, ThriftExchangeProtocol exchangeProtocol,
                                                 final ThriftNegotiationType negotiationType, final ThriftCompressionType compressionType, final CamelContext context) {
        Object clientInstance = null;
        Class[] constructorParamTypes = {TProtocol.class};
        Object[] constructorParamValues = {constructSyncProtocol(transport, exchangeProtocol, negotiationType, compressionType)};

        String clientClassName = packageName + "." + serviceName + "$" + ThriftConstants.THRIFT_SYNC_CLIENT_CLASS_NAME;
        try {
            Class clientClass = context.getClassResolver().resolveMandatoryClass(clientClassName);
            Constructor clientConstructor = clientClass.getConstructor(constructorParamTypes);
            clientInstance = clientConstructor.newInstance(constructorParamValues);

        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Thrift client class not found: " + clientClassName);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Thrift client class constructor not found: " + clientClassName);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | SecurityException e) {
            throw new IllegalArgumentException(e);
        }
        return clientInstance;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object constructAsyncClientInstance(String packageName, String serviceName, TNonblockingTransport transport, ThriftExchangeProtocol exchangeProtocol, final CamelContext context) {
        Object asynClientInstance = null;
        Class[] getterParamTypes = {TNonblockingTransport.class};
        Class[] constructorParamTypes = {TAsyncClientManager.class, TProtocolFactory.class};

        String clientClassName = packageName + "." + serviceName + "$" + ThriftConstants.THRIFT_ASYNC_CLIENT_CLASS_NAME + "$" + ThriftConstants.THRIFT_ASYNC_CLIENT_FACTORY_NAME;
        try {
            Class clientClass = context.getClassResolver().resolveMandatoryClass(clientClassName);
            Constructor factoryConstructor = clientClass.getConstructor(constructorParamTypes);
            Object factoryInstance = factoryConstructor.newInstance(new TAsyncClientManager(), constructAsyncProtocol(exchangeProtocol));
            Method asyncClientGetter = ReflectionHelper.findMethod(clientClass, ThriftConstants.THRIFT_ASYNC_CLIENT_GETTER_NAME, getterParamTypes);
            if (asyncClientGetter == null) {
                throw new IllegalArgumentException("Thrift async client getter not found: " + clientClassName + "." + ThriftConstants.THRIFT_ASYNC_CLIENT_GETTER_NAME);
            }
            asynClientInstance = ObjectHelper.invokeMethod(asyncClientGetter, factoryInstance, (TNonblockingTransport)transport);

        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Thrift sync client class not found: " + clientClassName);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Thrift sync client factory class not found: " + clientClassName);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | IOException | SecurityException e) {
            throw new IllegalArgumentException(e);
        }
        return asynClientInstance;
    }

    @SuppressWarnings({"rawtypes"})
    public static Object invokeSyncMethod(Object syncClient, String invokeMethod, Object request) {
        Object[] params = convertObjects2Primitives(request, null);
        Class[] paramsTypes = (Class[])params[0];
        Object[] paramsValues = (Object[])params[1];

        Method method = ReflectionHelper.findMethod(syncClient.getClass(), invokeMethod, paramsTypes);
        if (method == null) {
            throw new IllegalArgumentException("Thrift service client method not found: " + syncClient.getClass().getName() + "." + invokeMethod + printParamsTypes(paramsTypes));
        }
        Object result = ObjectHelper.invokeMethod(method, syncClient, paramsValues);
        return result;
    }

    @SuppressWarnings({"rawtypes"})
    public static void invokeAsyncMethod(Object asyncClient, String invokeMethod, Object request, AsyncClientMethodCallback methodCallback) {
        Object[] params = convertObjects2Primitives(request, methodCallback);
        Class[] paramsTypes = (Class[])params[0];
        Object[] paramsValues = (Object[])params[1];

        Method method = ReflectionHelper.findMethod(asyncClient.getClass(), invokeMethod, paramsTypes);
        if (method == null) {
            throw new IllegalArgumentException("Thrift service client method not found: " + asyncClient.getClass().getName() + "." + invokeMethod + printParamsTypes(paramsTypes));
        }
        ObjectHelper.invokeMethod(method, asyncClient, paramsValues);
    }

    @SuppressWarnings("rawtypes")
    public static Class getServerInterface(String packageName, String serviceName, boolean isSyncInterface, final CamelContext context) {
        String serverInterfaceName = null;
        Class serverInterface = null;

        try {
            if (isSyncInterface) {
                serverInterfaceName = packageName + "." + serviceName + "$" + ThriftConstants.THRIFT_SERVER_SYNC_INTERFACE_NAME;
            } else {
                serverInterfaceName = packageName + "." + serviceName + "$" + ThriftConstants.THRIFT_SERVER_ASYNC_INTERFACE_NAME;
            }
            serverInterface = context.getClassResolver().resolveMandatoryClass(serverInterfaceName);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unable to find server interface implementation for: " + serverInterfaceName);
        }
        return serverInterface;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object constructServerProcessor(String packageName, String serviceName, Object serverImplementation, boolean isSyncProcessor, final CamelContext context) {
        String processorClassName = null;
        Class serverInterface = null;
        Object processorInstance = null;

        try {
            if (isSyncProcessor) {
                processorClassName = packageName + "." + serviceName + "$" + ThriftConstants.THRIFT_SERVER_SYNC_PROCESSOR_CLASS;
                serverInterface = getServerInterface(packageName, serviceName, isSyncProcessor, context);
            } else {
                processorClassName = packageName + "." + serviceName + "$" + ThriftConstants.THRIFT_SERVER_ASYNC_PROCESSOR_CLASS;
                serverInterface = getServerInterface(packageName, serviceName, isSyncProcessor, context);
            }
            Class processorClass = context.getClassResolver().resolveMandatoryClass(processorClassName);
            Constructor procesorConstructor = processorClass.getConstructor(new Class[] {serverInterface});
            processorInstance = procesorConstructor.newInstance(serverImplementation);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unable to find server processor for: " + processorClassName);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new IllegalArgumentException("Processor class instance not found for: " + processorClassName);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new IllegalArgumentException(e);
        }
        return processorInstance;
    }
    
    private static TProtocol constructSyncProtocol(TTransport transport, ThriftExchangeProtocol exchangeProtocol,
                                                   final ThriftNegotiationType negotiationType, final ThriftCompressionType compressionType) {
        if (negotiationType == ThriftNegotiationType.SSL) {
            // If negotiation passed over SSL/TLS the only binary transport is supported
            return new TBinaryProtocol(transport);
        } else if (compressionType == ThriftCompressionType.ZLIB) {
            return new TBinaryProtocol(new TZlibTransport(transport));
        } else {
            switch (exchangeProtocol) {
            case BINARY:
                return new TBinaryProtocol(new TFramedTransport(transport));
            case JSON:
                return new TJSONProtocol(new TFramedTransport(transport));
            case SJSON:
                return new TSimpleJSONProtocol(new TFramedTransport(transport));
            case COMPACT:
                return new TCompactProtocol(new TFramedTransport(transport));
            default:
                throw new IllegalArgumentException("Exchange protocol " + exchangeProtocol + " not implemented");
            }
        }
    }
    
    private static TProtocolFactory constructAsyncProtocol(ThriftExchangeProtocol exchangeProtocol) {
        switch (exchangeProtocol) {
        case BINARY:
            return new TBinaryProtocol.Factory();
        case JSON:
            return new TJSONProtocol.Factory();
        case SJSON:
            return new TSimpleJSONProtocol.Factory();
        case COMPACT:
            return new TCompactProtocol.Factory();
        default:
            throw new IllegalArgumentException("Exchange protocol " + exchangeProtocol + " not implemented");    
        }
    }

    /**
     * The function find onComplete method inside interface implementation and
     * get fist parameter (but not Object.class) as return type
     */
    @SuppressWarnings("rawtypes")
    public static Class findMethodReturnType(Class clazz, String name) {
        for (Method method : clazz.getMethods()) {
            if (name.equals(method.getName()) && !method.getParameterTypes()[0].equals(Object.class)) {
                return method.getParameterTypes()[0];
            }
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    private static String printParamsTypes(Class[] paramsTypes) {
        StringJoiner joiner = new StringJoiner(",");
        for (Class paramType : paramsTypes) {
            joiner.add(paramType == null ? "null" : paramType.getName());
        }
        return "(" + joiner.toString() + ")";
    }

    /**
     * The function transforms objects types stored as list or simple object
     * inside the Body to the primitives objects to find appropriate method
     */
    @SuppressWarnings({"rawtypes"})
    private static Object[] convertObjects2Primitives(Object request, AsyncClientMethodCallback methodCallback) {
        Class[] paramsTypes = null;
        Object[] paramsValues = null;
        int paramListSize = 1;
        if (request instanceof List) {
            List paramList = (List)request;
            paramListSize = paramList.size() + (methodCallback == null ? 0 : 1);
            paramsTypes = new Class[paramListSize];
            paramsValues = new Object[paramListSize];
            int idx = 0;

            for (Object param : paramList) {
                if (param instanceof Short) {
                    paramsTypes[idx] = short.class;
                } else if (param instanceof Long) {
                    paramsTypes[idx] = long.class;
                } else if (param instanceof Integer) {
                    paramsTypes[idx] = int.class;
                } else if (param instanceof Double) {
                    paramsTypes[idx] = double.class;
                } else if (param instanceof Byte) {
                    paramsTypes[idx] = byte.class;
                } else if (param instanceof Boolean) {
                    paramsTypes[idx] = boolean.class;
                } else if (param instanceof List) {
                    paramsTypes[idx] = List.class;
                } else if (param instanceof Set) {
                    paramsTypes[idx] = Set.class;
                } else if (param instanceof Map) {
                    paramsTypes[idx] = Map.class;
                } else if (param instanceof ByteBuffer) {
                    paramsTypes[idx] = ByteBuffer.class;
                } else {
                    paramsTypes[idx] = param.getClass();
                }
                paramsValues[idx] = param;
                idx++;
            }
        } else if (request != null) {
            paramListSize = methodCallback == null ? 1 : 2;
            paramsTypes = new Class[paramListSize];
            paramsValues = new Object[paramListSize];
            paramsTypes[0] = request.getClass();
            paramsValues[0] = request;
        } else {
            paramListSize = methodCallback == null ? 0 : 1;
            paramsTypes = new Class[paramListSize];
            paramsValues = new Object[paramListSize];
        }
        if (methodCallback != null) {
            paramsTypes[paramListSize - 1] = AsyncMethodCallback.class;
            paramsValues[paramListSize - 1] = methodCallback;
        }
        return new Object[] {paramsTypes, paramsValues};
    }
}