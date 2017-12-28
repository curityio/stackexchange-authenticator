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
import se.curity.identityserver.sdk.attribute.Attribute;
import se.curity.identityserver.sdk.authentication.AuthenticationResult;
import se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler;
import se.curity.identityserver.sdk.errors.ErrorCode;
import se.curity.identityserver.sdk.service.ExceptionFactory;
import se.curity.identityserver.sdk.service.Json;
import se.curity.identityserver.sdk.service.authentication.AuthenticatorInformationProvider;
import se.curity.identityserver.sdk.web.Request;
import se.curity.identityserver.sdk.web.Response;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.curity.identityserver.plugin.stackexchange.descriptor.StackExchangeAuthenticatorPluginDescriptor.CALLBACK;
import static se.curity.identityserver.sdk.http.RedirectStatusCode.MOVED_TEMPORARILY;

public class StackExchangeAuthenticatorRequestHandler implements AuthenticatorRequestHandler<Request>
{
    private static final Logger _logger = LoggerFactory.getLogger(StackExchangeAuthenticatorRequestHandler.class);
    private static final String AUTHORIZATION_ENDPOINT = "https://stackexchange.com/oauth";

    private final StackExchangeAuthenticatorPluginConfig _config;
    private final ExceptionFactory _exceptionFactory;
    private final AuthenticatorInformationProvider _authenticatorInformationProvider;

    public StackExchangeAuthenticatorRequestHandler(StackExchangeAuthenticatorPluginConfig config,
                                                    ExceptionFactory exceptionFactory,
                                                    Json json,
                                                    AuthenticatorInformationProvider authenticatorInformationProvider)
    {
        _config = config;
        _exceptionFactory = exceptionFactory;
        _authenticatorInformationProvider = authenticatorInformationProvider;
    }

    @Override
    public Optional<AuthenticationResult> get(Request request, Response response)
    {
        _logger.info("GET request received for authentication authentication");

        URL redirectUri = createRedirectUri();
        String state = UUID.randomUUID().toString();
        Map<String, Collection<String>> queryStringArguments = new LinkedHashMap<>(5);
        Set<String> scopes = new LinkedHashSet<>(); // TODO: Capacity

        _config.getSessionManager().put(Attribute.of("state", state));

        // TODO: Build scopes from config

        addQueryString(queryStringArguments, "client_id", _config.getClientId());
        addQueryString(queryStringArguments, "redirect_uri", redirectUri.toString());
        addQueryString(queryStringArguments, "state", state);
        addQueryString(queryStringArguments, "response_type", "code");
        addQueryString(queryStringArguments, "scope", String.join(" ", scopes));

        _logger.debug("Redirecting to {} with query string arguments {}", AUTHORIZATION_ENDPOINT,
                queryStringArguments);

        throw _exceptionFactory.redirectException(AUTHORIZATION_ENDPOINT, MOVED_TEMPORARILY,
                queryStringArguments, false);
    }

    @Override
    public Optional<AuthenticationResult> post(Request requestModel, Response response)
    {
        throw _exceptionFactory.methodNotAllowed();
    }

    @Override
    public Request preProcess(Request request, Response response)
    {
        return request;
    }

    private static void addQueryString(Map<String, Collection<String>> queryStringArguments, String key, Object value)
    {
        queryStringArguments.put(key, Collections.singleton(value.toString()));
    }

    private URL createRedirectUri()
    {
        try
        {
            URI authUri = _authenticatorInformationProvider.getFullyQualifiedAuthenticationUri();

            return new URL(authUri.toURL(), authUri.getPath() + "/" + CALLBACK);
        }
        catch (MalformedURLException e)
        {
            throw _exceptionFactory.internalServerException(ErrorCode.INVALID_REDIRECT_URI,
                    "Could not create redirect URI");
        }
    }
}