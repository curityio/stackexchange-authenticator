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
import se.curity.identityserver.sdk.config.annotation.DefaultEnum;
import se.curity.identityserver.sdk.config.annotation.DefaultString;
import se.curity.identityserver.sdk.config.annotation.Description;
import se.curity.identityserver.sdk.service.ExceptionFactory;
import se.curity.identityserver.sdk.service.HttpClient;
import se.curity.identityserver.sdk.service.Json;
import se.curity.identityserver.sdk.service.SessionManager;
import se.curity.identityserver.sdk.service.WebServiceClientFactory;
import se.curity.identityserver.sdk.service.authentication.AuthenticatorInformationProvider;

import java.util.Optional;

@SuppressWarnings("InterfaceNeverImplemented")
public interface StackExchangeAuthenticatorPluginConfig extends Configuration {
    @Description("Client id")
    String getClientId();

    @Description("Secret key used for communication with StackExchange")
    String getClientSecret();

    @Description("StackExchange App Key")
    String getAppKey();

    @Description("A space-separated list of scopes to request from StackExchange")
    @DefaultString("")
    String getScope();

    @Description("The HTTP client with any proxy and TLS settings that will be used to connect to stackexchange.com and api.stackexchange.com")
    Optional<HttpClient> getHttpClient();

    @DefaultEnum("stackoverflow")
    Site getSite();

    @SuppressWarnings({"SpellCheckingInspection", "unused"})
    enum Site
    {
        stackoverflow,
        serverfault,
        superuser,
        meta,
        webapps,
        gaming,
        webmasters,
        cooking,
        gamedev,
        photo,
        stats,
        math,
        diy,
        gis,
        tex,
        askubuntu,
        money,
        english,
        stackapps,
        ux,
        unix,
        wordpress,
        cstheory,
        apple,
        rpg,
        bicycles,
        softwareengineering,
        electronics,
        android,
        boardgames,
        physics,
        homebrew,
        security,
        writers,
        video,
        graphicdesign,
        dba,
        scifi,
        codereview,
        codegolf,
        quant,
        pm,
        skeptics,
        fitness,
        drupal,
        mechanics,
        parenting,
        sharepoint,
        music,
        sqa,
        judaism,
        german,
        japanese,
        philosophy,
        gardening,
        travel,
        productivity,
        crypto,
        dsp,
        french,
        christianity,
        bitcoin,
        linguistics,
        hermeneutics,
        history,
        bricks,
        spanish,
        scicomp,
        movies,
        chinese,
        biology,
        poker,
        mathematica,
        psychology,
        outdoors,
        martialarts,
        sports,
        academia,
        cs,
        workplace,
        windowsphone,
        chemistry,
        chess,
        raspberrypi,
        russian,
        islam,
        salesforce,
        patents,
        genealogy,
        robotics,
        expressionengine,
        politics,
        anime,
        magento,
        ell,
        sustainability,
        tridion,
        reverseengineering,
        networkengineering,
        opendata,
        freelancing,
        blender,
        mathoverflow,
        space,
        sound,
        astronomy,
        tor,
        pets,
        ham,
        italian,
        pt,
        aviation,
        ebooks,
        alcohol,
        softwarerecs,
        arduino,
        expatriates,
        matheducators,
        earthscience,
        joomla,
        datascience,
        puzzling,
        craftcms,
        buddhism,
        hinduism,
        communitybuilding,
        worldbuilding,
        ja,
        emacs,
        hsm,
        economics,
        lifehacks,
        engineering,
        coffee,
        vi,
        musicfans,
        woodworking,
        civicrm,
        health,
        ru,
        rus,
        mythology,
        law,
        opensource,
        elementaryos,
        portuguese,
        computergraphics,
        hardwarerecs,
        es,
        ethereum,
        latin,
        languagelearning,
        retrocomputing,
        crafts,
        korean,
        monero,
        ai,
        esperanto,
        sitecore,
        iot,
        literature,
        vegetarianism,
        ukrainian,
        devops,
        bioinformatics,
        cseducators,
        interpersonal,
        augur,
        iota;

    }

    // Services that don't require any configuration

    AuthenticatorInformationProvider getAuthenticatorInformationProvider();

    SessionManager getSessionManager();

    WebServiceClientFactory getWebServiceClientFactory();

    Json getJson();

    ExceptionFactory getExceptionFactory();
}
