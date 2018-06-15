/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.ballerinalang.net.grpc;

import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.LastHttpContent;
import org.ballerinalang.connector.api.BallerinaConnectorException;
import org.ballerinalang.connector.api.Value;
import org.ballerinalang.net.grpc.listener.ServerCallHandler;
import org.ballerinalang.net.http.HttpUtil;
import org.ballerinalang.runtime.threadpool.ThreadPoolFactory;
import org.ballerinalang.util.exceptions.BallerinaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.contract.HttpConnectorListener;
import org.wso2.transport.http.netty.message.HTTPCarbonMessage;

import java.io.PrintStream;
import java.util.concurrent.Executor;

import static org.ballerinalang.net.grpc.GrpcConstants.DEFAULT_MAX_MESSAGE_SIZE;

/**
 * HTTP connector listener for Ballerina.
 */
public class ServerConnectorListener implements HttpConnectorListener {

    private static final Logger log = LoggerFactory.getLogger(ServerConnectorListener.class);
    protected static final String HTTP_RESOURCE = "httpResource";
    private static final PrintStream console = System.out;

    private final ServicesRegistry servicesRegistry;

    private final Value[] filterHolders;

    public ServerConnectorListener(ServicesRegistry servicesRegistry,
                                   Value[] filterHolders) {

        this.servicesRegistry = servicesRegistry;
        this.filterHolders = filterHolders;
    }

    @Override
    public void onMessage(HTTPCarbonMessage inboundMessage) {

        try {
            InboundMessage request = new InboundMessage(inboundMessage);
            if (!isValid(request)) {
                return;
            }
            OutboundMessage outboundMessage = new OutboundMessage(request);

            // Remove the leading slash of the path and get the fully qualified method name
            CharSequence path = request.getPath();
            String method = path != null ? path.subSequence(1, path.length()).toString() : null;

            deliver(method, request, outboundMessage);

        } catch (BallerinaException ex) {
            try {
                HttpUtil.handleFailure(inboundMessage, new BallerinaConnectorException(ex.getMessage(), ex.getCause()));
            } catch (Exception e) {
                log.error("Cannot handle error using the error handler for: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        log.error("Error in http server connector" + throwable.getMessage(), throwable);
    }

    private void deliver(String method, InboundMessage inboundMessage, OutboundMessage outboundMessage) {

        ServerMethodDefinition methodDefinition = servicesRegistry.lookupMethod(method);

        if (methodDefinition == null) {
            handleFailure(inboundMessage.getHttpCarbonMessage(), 404, Status.Code.UNIMPLEMENTED, String.format
                    ("Method not found: %s", method));
            return;
        }

        final Executor wrappedExecutor = ThreadPoolFactory.getInstance().getWorkerExecutor();
        wrappedExecutor.execute(() -> {
            ServerStreamListener listener;

            try {
                listener = startCall(inboundMessage, outboundMessage, method);
                InboundStateListener stateListener = new InboundStateListener(DEFAULT_MAX_MESSAGE_SIZE, listener);
                stateListener.setDecompressor(inboundMessage.getMessageDecompressor());

                HttpContent httpContent = inboundMessage.getHttpCarbonMessage().getHttpContent();
                while (true) {
                    if (httpContent == null) {
                        break;
                    }
                    // Exit the loop at the end of the content
                    if (httpContent instanceof LastHttpContent) {
                        stateListener.inboundDataReceived(new NettyReadableBuffer(httpContent.content()),
                                true);
                        break;
                    } else {
                        stateListener.inboundDataReceived(new NettyReadableBuffer(httpContent.content()),
                                false);
                    }
                    httpContent = inboundMessage.getHttpCarbonMessage().getHttpContent();
                }
            } catch (RuntimeException | Error e) {
                HttpUtil.handleFailure(inboundMessage.getHttpCarbonMessage(), new BallerinaConnectorException(e
                        .getMessage(), e.getCause()));
                throw e;
            }
        });
    }


    private <ReqT, RespT> ServerStreamListener startCall(InboundMessage inboundMessage, OutboundMessage
            outboundMessage, String fullMethodName) {
        // Get method definition of the inboundMessage.
        ServerMethodDefinition<ReqT, RespT> methodDefinition = (ServerMethodDefinition<ReqT, RespT>)
                servicesRegistry.lookupMethod(fullMethodName);
        // Create service call instance for the inboundMessage.
        ServerCallImpl<ReqT, RespT> call = new ServerCallImpl<>(inboundMessage, outboundMessage, methodDefinition
                .getMethodDescriptor(), DecompressorRegistry.getDefaultInstance(), CompressorRegistry
                .getDefaultInstance());;
        ServerCallHandler<ReqT, RespT> callHandler = methodDefinition.getServerCallHandler();

        ServerCall.Listener<ReqT> listener = callHandler.startCall(call);
        if (listener == null) {
            throw new NullPointerException(
                    "startCall() returned a null listener for method " + fullMethodName);
        }
        return call.newServerStreamListener(listener);
    }

    private boolean isValid(InboundMessage inboundMessage) {

        HttpHeaders headers = inboundMessage.getHeaders();

        // Validate inboundMessage path.
        CharSequence path = inboundMessage.getPath();

        if (path == null) {
            handleFailure(inboundMessage.getHttpCarbonMessage(), 404, Status.Code.UNIMPLEMENTED, "Expected path is " +
                    "missing");
            return false;
        }

        if (path.charAt(0) != '/') {
            handleFailure(inboundMessage.getHttpCarbonMessage(), 404, Status.Code.UNIMPLEMENTED, String.format
                    ("Expected path to start with /: %s", path));
            return false;
        }

        // Verify that the Content-Type is correct in the inboundMessage.
        CharSequence contentType = headers.get("content-type");
        if (contentType == null) {
            handleFailure(inboundMessage.getHttpCarbonMessage(), 415, Status.Code.INTERNAL, "Content-Type is " +
                    "missing from the request");
            return false;
        }
        String contentTypeString = contentType.toString();
        if (!MessageUtils.isGrpcContentType(contentTypeString)) {
            handleFailure(inboundMessage.getHttpCarbonMessage(), 415, Status.Code.INTERNAL, String.format
                    ("Content-Type '%s' is not supported", contentTypeString));
            return false;
        }

        String method = inboundMessage.getHttpMethod();
        if (!"POST".equals(method)) {
            handleFailure(inboundMessage.getHttpCarbonMessage(), 405, Status.Code.INTERNAL, String.format("Method " +
                    "'%s' is not supported", method));
            return false;
        }

        return true;
    }

    private void handleFailure(HTTPCarbonMessage requestMessage, int status, Status.Code statusCode, String msg) {

        HTTPCarbonMessage responseMessage = HttpUtil.createErrorMessage(msg, status);
        responseMessage.setHeader("grpc-status", statusCode.toString());
        responseMessage.setHeader("grpc-message", msg);

        HttpUtil.sendOutboundResponse(requestMessage, responseMessage);
    }

    private static class InboundStateListener extends InboundMessage.InboundStateListener {

        final ServerStreamListener listener;

        protected InboundStateListener(int maxMessageSize, ServerStreamListener listener) {

            super(maxMessageSize);
            this.listener = listener;
        }

        @Override
        protected ServerStreamListener listener() {

            return listener;
        }

        @Override
        public void deframerClosed(boolean hasPartialMessage) {

            if (hasPartialMessage) {
                deframeFailed(
                        Status.Code.INTERNAL.toStatus()
                                .withDescription("Encountered end-of-stream mid-frame")
                                .asRuntimeException());
                return;
            }
            listener.halfClosed();
        }

        /**
         * Called in the transport thread to process the content of an inbound DATA frame from the
         * client.
         *
         * @param frame       the inbound HTTP/2 DATA frame. If this buffer is not used immediately, it must
         *                    be retained.
         * @param endOfStream {@code true} if no more data will be received on the stream.
         */
        public void inboundDataReceived(ReadableBuffer frame, boolean endOfStream) {

            // Deframe the message. If a failure occurs, deframeFailed will be called.
            deframe(frame);
            if (endOfStream) {
                closeDeframer(false);
            }
        }
    }

}
