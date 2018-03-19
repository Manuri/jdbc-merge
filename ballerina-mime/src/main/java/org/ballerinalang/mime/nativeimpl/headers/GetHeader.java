/*
*  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/

package org.ballerinalang.mime.nativeimpl.headers;

import io.netty.handler.codec.http.HttpHeaders;
import org.ballerinalang.bre.Context;
import org.ballerinalang.bre.bvm.BlockingNativeCallableUnit;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.model.values.BString;
import org.ballerinalang.model.values.BStruct;
import org.ballerinalang.natives.annotations.Argument;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.ballerinalang.natives.annotations.Receiver;
import org.ballerinalang.natives.annotations.ReturnType;

import static org.ballerinalang.mime.util.Constants.ENTITY_HEADERS;
import static org.ballerinalang.mime.util.Constants.FIRST_PARAMETER_INDEX;

/**
 * Get the header value associated with the given header name.
 *
 * @since 0.966.0
 */
@BallerinaFunction(
        packageName = "ballerina.mime",
        functionName = "getHeader",
        receiver = @Receiver(type = TypeKind.STRUCT, structType = "Entity", structPackage = "ballerina.mime"),
        args = {@Argument(name = "headerName", type = TypeKind.STRING)},
        returnType = {@ReturnType(type = TypeKind.STRING)},
        isPublic = true
)
public class GetHeader extends BlockingNativeCallableUnit {

    @Override
    public void execute(Context context) {
        BStruct entityStruct = (BStruct) context.getRefArgument(FIRST_PARAMETER_INDEX);
        String headerName = context.getStringArgument(FIRST_PARAMETER_INDEX);
        if (entityStruct.getNativeData(ENTITY_HEADERS) == null) {
            context.setReturnValues(null);
            return;
        }
        HttpHeaders httpHeaders = (HttpHeaders) entityStruct.getNativeData(ENTITY_HEADERS);
        if (httpHeaders != null && httpHeaders.get(headerName) != null && !httpHeaders.get(headerName).isEmpty()) {
            context.setReturnValues(new BString(httpHeaders.get(headerName)));
        } else {
            context.setReturnValues();
        }
    }
}
