/*
 *  Copyright 2017 Curity AB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.curity.identityserver.plugin.stackexchange.config;

import se.curity.identityserver.sdk.config.Configuration;
import se.curity.identityserver.sdk.config.annotation.DefaultString;
import se.curity.identityserver.sdk.config.annotation.Description;
import se.curity.identityserver.sdk.service.SessionManager;
import se.curity.identityserver.sdk.service.WebServiceClient;

@SuppressWarnings("InterfaceNeverImplemented")
public interface StackExchangeAuthenticatorPluginConfig extends Configuration {
    @Description("Client id")
    String getClientId();

    @Description("Secret key used for communication with StackExchange")
    String getClientSecret();

    @Description("StackExchange App Key")
    String getAppKey();

    WebServiceClient getWebServiceClient();

    @Description("A space-separated list of scopes to request from StackExchange")
    @DefaultString("")
    String getScope();

    SessionManager getSessionManager();

    @DefaultString("stackoverflow")
    String getSite();
}
