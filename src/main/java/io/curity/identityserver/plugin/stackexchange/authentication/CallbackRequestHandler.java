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

package io.curity.identityserver.plugin.stackexchange.authentication;

import com.google.common.collect.ImmutableMap;
import io.curity.identityserver.plugin.authentication.DefaultOAuthClient;
import io.curity.identityserver.plugin.authentication.OAuthClient;
import io.curity.identityserver.plugin.stackexchange.config.StackExchangeAuthenticatorPluginConfig;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.curity.identityserver.sdk.attribute.Attribute;
import se.curity.identityserver.sdk.attribute.Attributes;
import se.curity.identityserver.sdk.attribute.AuthenticationAttributes;
import se.curity.identityserver.sdk.attribute.ContextAttributes;
import se.curity.identityserver.sdk.attribute.SubjectAttributes;
import se.curity.identityserver.sdk.authentication.AuthenticationResult;
import se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler;
import se.curity.identityserver.sdk.errors.ErrorCode;
import se.curity.identityserver.sdk.service.ExceptionFactory;
import se.curity.identityserver.sdk.service.Json;
import se.curity.identityserver.sdk.service.authentication.AuthenticatorInformationProvider;
import se.curity.identityserver.sdk.web.Request;
import se.curity.identityserver.sdk.web.Response;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import static io.curity.identityserver.plugin.authentication.Constants.Params.PARAM_ACCESS_TOKEN;

public class CallbackRequestHandler
        implements AuthenticatorRequestHandler<CallbackGetRequestModel> {

    private static final Logger _logger = LoggerFactory.getLogger(CallbackRequestHandler.class);

    private final ExceptionFactory _exceptionFactory;
    private final OAuthClient _oauthClient;
    private final StackExchangeAuthenticatorPluginConfig _config;
    private final HttpClient _client;

    public CallbackRequestHandler(ExceptionFactory exceptionFactory,
                                  AuthenticatorInformationProvider provider,
                                  Json json,
                                  StackExchangeAuthenticatorPluginConfig config) {
        _exceptionFactory = exceptionFactory;
        _oauthClient = new DefaultOAuthClient(exceptionFactory, provider, json, config.getSessionManager());
        _config = config;
        _client = HttpClientBuilder.create().build();
    }

    @Override
    public CallbackGetRequestModel preProcess(Request request, Response response) {
        if (request.isGetRequest()) {
            return new CallbackGetRequestModel(request);
        } else {
            throw _exceptionFactory.methodNotAllowed();
        }
    }

    @Override
    public Optional<AuthenticationResult> get(CallbackGetRequestModel requestModel,
                                              Response response) {
        _oauthClient.redirectToAuthenticationOnError(requestModel.getRequest(), _config.id());

        HttpResponse tokensResponse = _oauthClient.getTokensResponse(_config.getTokenEndpoint().toString(),
                _config.getClientId(),
                _config.getClientSecret(),
                requestModel.getCode(),
                requestModel.getState());
        Map<String, String> tokenMap = null;
        try {
            tokenMap = DefaultOAuthClient.splitQuery(new URL(_config.getTokenEndpoint() + "?" + EntityUtils.toString(tokensResponse.getEntity())));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Optional<AuthenticationResult> authenticationResult = getAuthenticationResult(tokenMap.get(PARAM_ACCESS_TOKEN).toString());

        return authenticationResult;
    }

    @Override
    public Optional<AuthenticationResult> post(CallbackGetRequestModel requestModel,
                                               Response response) {
        throw _exceptionFactory.methodNotAllowed();
    }


    public Optional<AuthenticationResult> getAuthenticationResult(String accessToken) {
        try {
            HttpGet request = new HttpGet(buildUrl(_config.getUserInfoEndpoint().toString(), ImmutableMap.of(PARAM_ACCESS_TOKEN, accessToken, "key", _config.getKey(), "site", "stackoverflow")));
            HttpResponse response = _client.execute(request);
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                _logger.debug("Got error response from user info endpoint {}", response.getStatusLine());

                throw _exceptionFactory.internalServerException(ErrorCode.INVALID_SERVER_STATE, "INTERNAL SERVER ERROR");
            }

            Map<String, Object> profileData = _oauthClient.parseResponse(response);

            String userId = ((Map) ((ArrayList) profileData.get("items")).get(0)).get("user_id").toString();
            AuthenticationAttributes attributes = AuthenticationAttributes.of(
                    SubjectAttributes.of(userId, Attributes.fromMap(profileData)),
                    ContextAttributes.of(Attributes.of(Attribute.of(PARAM_ACCESS_TOKEN, accessToken))));
            AuthenticationResult authenticationResult = new AuthenticationResult(attributes);

            return Optional.of(authenticationResult);

        } catch (IOException e) {
            _logger.warn("Could not communicate with profile endpoint", e);

            throw _exceptionFactory.internalServerException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Authentication failed");
        }
    }

    public String buildUrl(String endpoint, Map<String, String> extraParams) {
        URIBuilder builder;

        try {
            builder = new URIBuilder(endpoint);
        } catch (URISyntaxException e) {
            _logger.warn("Bad syntax in redirect url", e);
            throw _exceptionFactory.configurationException("Bad syntax in redirect url");
        }

        for (String key : extraParams.keySet()) {
            builder.addParameter(key, extraParams.get(key));
        }

        return builder.toString();
    }

}
