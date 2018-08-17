// Copyright (c) 2018 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/http;
import ballerina/log;

// Filter1

public type Filter10 object {
    public function filterRequest (http:Listener listener, http:Request request, http:FilterContext context) returns boolean {
        log:printInfo("Intercepting request for filter 1");
        return true;
    }

    public function filterResponse(http:Response response, http:FilterContext context) returns boolean {
        return true;
    }
};

Filter10 filter10;

// Filter2

public type Filter11 object {
    public function filterRequest (http:Listener listener, http:Request request, http:FilterContext context) returns boolean {
        endpoint http:Listener caller = listener;
        log:printInfo("Intercepting request for filter 2");
        http:Response response;
        response.statusCode = 403;
        response.setTextPayload("Authorization failure");
        var value = caller->respond(response);
        return false;
    }

    public function filterResponse(http:Response response, http:FilterContext context) returns boolean {
        return true;
    }
};

Filter11 filter11;

endpoint http:Listener echoEP04 {
    port:9094,
    filters:[filter10, filter11]
};

@http:ServiceConfig {
    basePath:"/echo"
}
service<http:Service> echo04 bind echoEP04 {
    @http:ResourceConfig {
        methods:["GET"],
        path:"/test"
    }
    echo (endpoint caller, http:Request req) {
        http:Response res = new;
        _ = caller -> respond(res);
    }
}
