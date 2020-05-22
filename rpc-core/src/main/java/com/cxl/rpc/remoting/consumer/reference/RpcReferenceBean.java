package com.cxl.rpc.remoting.consumer.reference;

import com.cxl.rpc.remoting.consumer.RpcInvokerFactory;
import com.cxl.rpc.remoting.consumer.call.CallBack;
import com.cxl.rpc.remoting.consumer.call.CallBackFactory;
import com.cxl.rpc.remoting.consumer.call.CallType;
import com.cxl.rpc.remoting.consumer.generic.RpcGenericService;
import com.cxl.rpc.remoting.consumer.route.LoadBalance;
import com.cxl.rpc.remoting.net.Client;
import com.cxl.rpc.remoting.net.NetEnum;
import com.cxl.rpc.remoting.net.params.RpcRequest;
import com.cxl.rpc.remoting.provider.RpcProviderFactory;
import com.cxl.rpc.serialize.Serializer;
import com.cxl.rpc.util.ClassUtil;
import com.cxl.rpc.util.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.util.TreeSet;
import java.util.UUID;

public class RpcReferenceBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(RpcReferenceBean.class);

    //-------------------config-------------------
    private NetEnum netType = NetEnum.NETTY;
    private Serializer serializer = Serializer.SerializerEnum.PROTOSTUFF.getSerializer();
    private CallType callType = CallType.SYNC;
    private LoadBalance loadBalance = LoadBalance.ROUND;

    private Class<?> iface;
    private String version;

    private long timeout = 1000;

    private String address;
    private String accessToken;

//    private RpcInvokeCallback invokeCallback;

    private RpcInvokerFactory invokerFactory;

    private CallBackFactory callBackFactory;

    public NetEnum getNetType() {
        return netType;
    }

    public void setNetType(NetEnum netType) {
        this.netType = netType;
    }

    public void setSerializer(Serializer serializer) {
        this.serializer = serializer;
    }

    public CallType getCallType() {
        return callType;
    }

    public void setCallType(CallType callType) {
        this.callType = callType;
    }

    public LoadBalance getLoadBalance() {
        return loadBalance;
    }

    public void setLoadBalance(LoadBalance loadBalance) {
        this.loadBalance = loadBalance;
    }

    public Class<?> getIface() {
        return iface;
    }

    public void setIface(Class<?> iface) {
        this.iface = iface;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

//    public RpcInvokeCallback getInvokeCallback() {
//        return invokeCallback;
//    }

//    public void setInvokeCallback(RpcInvokeCallback invokeCallback) {
//        this.invokeCallback = invokeCallback;
//    }

    public void setInvokerFactory(RpcInvokerFactory invokerFactory) {
        this.invokerFactory = invokerFactory;
    }


    public RpcReferenceBean() {
        // init Client
//        initClient();
    }

    //get
    public Serializer getSerializer() {
        return serializer;
    }

    public long getTimeout() {
        return timeout;
    }

    public RpcInvokerFactory getInvokerFactory() {
        return invokerFactory;
    }

    // ---------------------- initClient ----------------------
    private Client client = null;

    public Client getClient() {
        return client;
    }

    public void initClient() {
        if (null == this.netType) {
            throw new RpcException("rpc reference netType missing.");
        }
        if (null == this.serializer) {
            throw new RpcException("rpc reference serializer missing.");
        }
        if (null == this.callType) {
            throw new RpcException("rpc reference callType missing.");
        }
        if (null == this.loadBalance) {
            throw new RpcException("rpc reference loadBalance missing.");
        }
        if (null == this.iface) {
            throw new RpcException("rpc reference iface missing.");
        }
        if (0 >= this.timeout) {
            this.timeout = 0;
        }

        if (null == this.invokerFactory) {
            this.invokerFactory = RpcInvokerFactory.getInstance();
        }
        if (null == this.callBackFactory) {
            callBackFactory = CallBackFactory.getInstance();
        }
        try {
            client = netType.clientClass.newInstance();
            client.init(this);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RpcException(e);
        }
//        return this;
    }


    //---------------------util-----------------

    public Object getObject() {
        initClient();
        return Proxy.newProxyInstance(Thread.currentThread()
                .getContextClassLoader(), new Class[]{iface}, (proxy, method, args) -> {
            //method param
            String className = method.getDeclaringClass().getName();//iface.getName

            String version1 = version;
            String methodName = method.getName();

            Class<?>[] parameterTypes = method.getParameterTypes();

            Object[] parameters = args;


            //filter for generic
            if (className.equals(RpcGenericService.class.getName()) && methodName.equals("invoke")) {
                Class<?>[] paramTypes = null;
                if (args[3] != null) {
                    String[] paramTypes_str = (String[]) args[3];
                    if (paramTypes_str.length > 0) {
                        parameters = new Class[paramTypes_str.length];

                        for (int i = 0; i < paramTypes_str.length; i++) {
                            parameters[i] = ClassUtil.resolveClass(paramTypes_str[i]);
                        }
                    }
                }
                className = (String) args[0];
                version1 = (String) args[1];
                methodName = (String) args[2];
                parameterTypes = paramTypes;

                parameters = (Object[]) args[4];
            }

            //filter method like "Object.toString()"
            if (className.equals(Object.class.getName())) {
                LOGGER.info(">>>>>>>>>>>>>>>>>>>>>rpc proxy class-method not support [{}#{}]", className, methodName);
                throw new RpcException("rpc proxy class-method not support");
            }

            //address
            String finalAddress = address;
            if (finalAddress == null || finalAddress.length() == 0) {
                if (invokerFactory != null && invokerFactory.getServiceRegistry() != null) {
                    //discovery
                    String serviceKey = RpcProviderFactory.makeServiceKey(className, version1);
                    TreeSet<String> addressSet = invokerFactory.getServiceRegistry().discovery(serviceKey);

                    //load balance
                    if (addressSet.size() == 1) {
                        finalAddress = addressSet.first();
                        address = finalAddress;
                    } else {
                        finalAddress = loadBalance.rpcLoadBalance.route(serviceKey, addressSet);
                    }
                }
            }
            if (finalAddress == null || finalAddress.length() == 0) {
                throw new RpcException("rpc rpcReferenceBean[" + className + "]address empty");
            }

            //request
            RpcRequest request = new RpcRequest();
            request.setRequestId(UUID.randomUUID().toString());

            request.setCreateMillisTime(System.currentTimeMillis());
            request.setAccessToken(accessToken);
            request.setClassName(className);
            request.setMethodName(methodName);
            request.setParameterTypes(parameterTypes);
            request.setParameters(parameters);
            CallBack back = callBackFactory.create(callType.name());
            Object export = back.export(request, this);
            if (null != export) {
                return export;
            }

            //send
//            if (CallType.SYNC == callType) {
//                //future-response set
//                RpcFutureResponse futureResponse = new RpcFutureResponse(invokerFactory, request);
//
//                try {
//                    //do invoke
//                    client.asyncSend(finalAddress, request);
//
//
//                    //future get
//                    RpcResponse response = futureResponse.get(timeout, TimeUnit.MILLISECONDS);
//                    if (response.getErrorMsg() != null) {
//                        throw new RpcException(response.getErrorMsg());
//                    }
//                    return response.getResult();
//                } catch (Exception e) {
//                    LOGGER.info(">>>>>>>>>>>>>>>rpc ,invoke error , address:{}, rpcRequest:{}", finalAddress, request);
//                    throw (e instanceof RpcException) ? e : new RpcException(e);
//                } finally {
//                    //future-response remove
//                    futureResponse.removeInvokerFuture();
//                }
//            } else if (CallType.FUTURE == callType) {
//                //future-response set
//                RpcFutureResponse futureResponse = new RpcFutureResponse(invokerFactory, request);
//
//                try {
//                    //invoke future set
//                    RpcInvokeFuture invokeFuture = new RpcInvokeFuture(futureResponse);
//                    RpcInvokeFuture.setFuture(invokeFuture);
//
//
//                    //do invoke
//                    client.asyncSend(finalAddress, request);
//                    return null;
//                } catch (Exception e) {
//                    LOGGER.info(">>>>>>>>>>>>>>>>>>>>rpc, invoke error , address:{}, RpcRequest{}", finalAddress, request);
//
//                    //future-response remove
//                    futureResponse.removeInvokerFuture();
//                    throw (e instanceof RpcException) ? e : new RpcException(e);
//                }
//            } else if (CallType.CALLBACK == callType) {
//                //get callback
//                RpcInvokeCallback finalInvokeCallback = invokeCallback;
//                RpcInvokeCallback threadInvokeCallback = RpcInvokeCallback.getCallback();
//
//                if (threadInvokeCallback != null) {
//                    finalInvokeCallback = threadInvokeCallback;
//                }
//                if (finalInvokeCallback == null) {
//                    throw new RpcException("rpc RpcInvokeCallback CallType=" + CallType.CALLBACK.name() + ") cannot be null.");
//                }
//
//                //future-response set
//                RpcFutureResponse futureResponse = new RpcFutureResponse(invokerFactory, request, finalInvokeCallback);
//
//                try {
//                    client.asyncSend(finalAddress, request);
//                } catch (Exception e) {
//                    LOGGER.info(">>>>>>>>>>>>>>rpc , invoke error , address:{}, RpcRequest{}", finalAddress, request);
//
//                    //future-response remove
//                    futureResponse.removeInvokerFuture();
//
//                    throw (e instanceof RpcException) ? e : new RpcException(e);
//                }
//                return null;
//            } else if (CallType.ONEWAY == callType) {
//                client.asyncSend(finalAddress, request);
//                return null;
//            } else {
//                throw new RpcException("rpc callType[" + callType + "] invalid");
//            }
            return null;
        });
    }

    public Class<?> getObjectType() {
        return iface;
    }
}