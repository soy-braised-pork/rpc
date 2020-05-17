package com.cxl.rpc.remoting.invoker.call;

import com.cxl.rpc.remoting.invoker.RpcInvokerFactory;
import com.cxl.rpc.remoting.invoker.reference.RpcReferenceBean;
import com.cxl.rpc.remoting.net.Client;
import com.cxl.rpc.remoting.net.params.RpcFutureResponse;
import com.cxl.rpc.remoting.net.params.RpcRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CallBack {

    protected static final Logger LOGGER= LoggerFactory.getLogger(CallBack.class);
    protected RpcFutureResponse rpcFutureResponse;
    protected Client client;
    protected String address;

    public Object export(RpcRequest request, RpcReferenceBean rpcReferenceBean) {
        RpcInvokerFactory invokerFactory = rpcReferenceBean.getInvokerFactory();
        rpcFutureResponse = new RpcFutureResponse(invokerFactory, request);
        client = rpcReferenceBean.getClient();
        address = rpcReferenceBean.getAddress();
        try {
            return export(request);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public abstract Object export(RpcRequest request);
}