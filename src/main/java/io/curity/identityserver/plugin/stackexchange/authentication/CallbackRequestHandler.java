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

import io.curity.identityserver.plugin.stackexchange.config.StackExchangeAuthenticatorPluginConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.curity.identityserver.sdk.Nullable;
import se.curity.identityserver.sdk.attribute.Attribute;
import se.curity.identityserver.sdk.attribute.AuthenticationAttributes;
import se.curity.identityserver.sdk.attribute.ContextAttributes;
import se.curity.identityserver.sdk.attribute.SubjectAttributes;
import se.curity.identityserver.sdk.attribute.scim.v2.Name;
import se.curity.identityserver.sdk.attribute.scim.v2.multivalued.Photo;
import se.curity.identityserver.sdk.authentication.AuthenticationResult;
import se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler;
import se.curity.identityserver.sdk.errors.ErrorCode;
import se.curity.identityserver.sdk.http.HttpResponse;
import se.curity.identityserver.sdk.service.ExceptionFactory;
import se.curity.identityserver.sdk.service.HttpClient;
import se.curity.identityserver.sdk.service.Json;
import se.curity.identityserver.sdk.service.WebServiceClient;
import se.curity.identityserver.sdk.service.WebServiceClientFactory;
import se.curity.identityserver.sdk.service.authentication.AuthenticatorInformationProvider;
import se.curity.identityserver.sdk.web.Request;
import se.curity.identityserver.sdk.web.Response;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static se.curity.identityserver.sdk.http.HttpRequest.createFormUrlEncodedBodyProcessor;

public class CallbackRequestHandler
        implements AuthenticatorRequestHandler<CallbackGetRequestModel>
{
    private static final Logger _logger = LoggerFactory.getLogger(CallbackRequestHandler.class);

    private final ExceptionFactory _exceptionFactory;
    private final StackExchangeAuthenticatorPluginConfig _config;
    private final AuthenticatorInformationProvider _authenticatorInformationProvider;
    private final Json _json;
    private final WebServiceClientFactory _webServiceClientFactory;

    public CallbackRequestHandler(StackExchangeAuthenticatorPluginConfig config)
    {
        _exceptionFactory = config.getExceptionFactory();
        _config = config;
        _json = config.getJson();
        _webServiceClientFactory = config.getWebServiceClientFactory();
        _authenticatorInformationProvider = config.getAuthenticatorInformationProvider();
    }

    @Override
    public CallbackGetRequestModel preProcess(Request request, Response response)
    {
        if (request.isGetRequest())
        {
            return new CallbackGetRequestModel(request);
        }
        else
        {
            throw _exceptionFactory.methodNotAllowed();
        }
    }

    @Override
    public Optional<AuthenticationResult> get(CallbackGetRequestModel requestModel,
                                              Response response)
    {
        validateState(requestModel.getState());
        handleError(requestModel);

        Map<String, Object> tokenResponseData = redeemCodeForTokens(requestModel);
        @Nullable Object accessToken = tokenResponseData.get("access_token");
        Map<String, String> userInfoResponseData = getUserInfo(accessToken);
        List<Attribute> subjectAttributes = new LinkedList<>(), contextAttributes = new LinkedList<>();
        String name = userInfoResponseData.get("display_name");

        if (userInfoResponseData.size() == 0)
        {
            // User probably doesn't have an account with the configured site.
            _logger.debug("No user info for access token. Use probably doesn't have an account at {}",
                    _config.getSite());

            throw _exceptionFactory.redirectException(
                    _authenticatorInformationProvider.getAuthenticationBaseUri().toASCIIString());
        }

        subjectAttributes.add(Attribute.of("profileUrl", userInfoResponseData.get("link")));
        subjectAttributes.add(Attribute.of("subject", userInfoResponseData.get("account_id")));
        subjectAttributes.add(Attribute.of("country", userInfoResponseData.get("location")));
        subjectAttributes.add(Attribute.of("displayName", name));
        subjectAttributes.add(Attribute.of("name", Name.of(name)));
        subjectAttributes.add(Attribute.of("photo",
                Photo.of(userInfoResponseData.get("profile_image"), false)));
        subjectAttributes.add(Attribute.of("stack_exchange_id", userInfoResponseData.get("user_id")));
        subjectAttributes.add(Attribute.of("website", userInfoResponseData.get("website_url")));

        contextAttributes.add(Attribute.of("stack_exchange_access_token", Objects.toString(accessToken)));

        AuthenticationAttributes authenticationAttributes = AuthenticationAttributes.of(
                SubjectAttributes.of(subjectAttributes),
                ContextAttributes.of(contextAttributes));

        return Optional.of(new AuthenticationResult(authenticationAttributes));
    }

    private Map<String, String> getUserInfo(@Nullable Object accessToken)
    {
        if (accessToken == null)
        {
            _logger.warn("No access token was available. Cannot get user info.");

            throw _exceptionFactory.internalServerException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        HttpResponse userInfoResponse = getWebServiceClient()
                .withQueries(createQueryParameters(accessToken.toString(), _config.getAppKey(), _config.getSite()))
                .request()
                .get()
                .response();
        int statusCode = userInfoResponse.statusCode();

        if (statusCode != 200)
        {
            if (_logger.isWarnEnabled())
            {
                _logger.warn("Got an error response from the user info endpoint. Error = {}, {}", statusCode,
                        userInfoResponse.body(HttpResponse.asString()));
            }

            throw _exceptionFactory.internalServerException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        Object items = _json.fromJson(userInfoResponse.body(HttpResponse.asString()))
                .getOrDefault("items", Collections.emptyMap());
        Map<String, String> result = Collections.emptyMap();

        if (items instanceof List)
        {
            List i = (List)items;
            Object j;

            if (i.size() >= 1 && (j = i.get(0)) instanceof Map)
            {
                Map m = (Map)j;
                result = new LinkedHashMap<>(m.size());

                for (Object k : m.keySet())
                {
                    result.put(k.toString(), m.get(k).toString());
                }
            }
        }

        return result;
    }

    private WebServiceClient getWebServiceClient()
    {
        Optional<HttpClient> httpClient = _config.getHttpClient();

        if (httpClient.isPresent())
        {
            return _webServiceClientFactory.create(httpClient.get())
                    .withHost("api.stackexchange.com")
                    .withPath("/2.2/me");
        }
        else
        {
            return _webServiceClientFactory.create(URI.create("https://api.stackexchange.com/2.2/me"));
        }
    }

    private Map<String, Collection<String>> createQueryParameters(String accessToken, String appKey,
                                                                  StackExchangeAuthenticatorPluginConfig.Site site)
    {
        Map<String, Collection<String>> parameters = new LinkedHashMap<>(3);

        parameters.put("key", Collections.singleton(appKey));
        parameters.put("site", Collections.singleton(site.name()));
        parameters.put("access_token", Collections.singleton(accessToken));

        return parameters;
    }

    private Map<String, Object> redeemCodeForTokens(CallbackGetRequestModel requestModel)
    {
        URI uri = URI.create("https://stackexchange.com/oauth/access_token/json");
        HttpResponse tokenResponse = _webServiceClientFactory.create(uri)
                .request()
                .contentType("application/x-www-form-urlencoded")
                .body(createFormUrlEncodedBodyProcessor(createPostData(_config.getClientId(),
                        _config.getClientSecret(), requestModel.getCode(), requestModel.getRequestUrl())))
                .post()
                .response();
        int statusCode = tokenResponse.statusCode();

        if (statusCode != 200)
        {
            if (_logger.isInfoEnabled())
            {
                _logger.info("Got error response from token endpoint: error = {}, {}", statusCode,
                        tokenResponse.body(HttpResponse.asString()));
            }

            throw _exceptionFactory.internalServerException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        return _json.fromJson(tokenResponse.body(HttpResponse.asString()));
    }

    private void handleError(CallbackGetRequestModel requestModel)
    {
        if (!Objects.isNull(requestModel.getError()))
        {

            if ("access_denied".equals(requestModel.getError()))
            {
                _logger.debug("Got an error from StackExchange: {} - {}", requestModel.getError(),
                        requestModel.getErrorDescription());

                throw _exceptionFactory.redirectException(
                        _authenticatorInformationProvider.getAuthenticationBaseUri().toASCIIString());
            }

            _logger.warn("Got an error from StackExchange: {} - {}", requestModel.getError(),
                    requestModel.getErrorDescription());

            throw _exceptionFactory.externalServiceException("Login with StackExchange failed");
        }
    }

    private static Map<String, String> createPostData(String clientId, String clientSecret, String code, String callbackUri)
    {
        Map<String, String> data = new HashMap<>(5);

        data.put("client_id", clientId);
        data.put("client_secret", clientSecret);
        data.put("code", code);
        data.put("grant_type", "authorization_code");
        data.put("redirect_uri", callbackUri);

        return data;
    }

    @Override
    public Optional<AuthenticationResult> post(CallbackGetRequestModel requestModel,
                                               Response response)
    {
        throw _exceptionFactory.methodNotAllowed();
    }

    private void validateState(String state)
    {
        @Nullable Attribute sessionAttribute = _config.getSessionManager().get("state");

        if (sessionAttribute != null && state.equals(sessionAttribute.getValueOfType(String.class)))
        {
            _logger.debug("State matches session");
        }
        else
        {
            _logger.debug("State did not match session");

            throw _exceptionFactory.badRequestException(ErrorCode.INVALID_SERVER_STATE, "Bad state provided");
        }
    }
}
