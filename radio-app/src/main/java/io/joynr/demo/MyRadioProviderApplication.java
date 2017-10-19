package io.joynr.demo;

/*
 * #%L
 * %%
 * Copyright (C) 2011 - 2017 BMW Car IT GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import io.joynr.provider.ProviderAnnotations;
import io.joynr.accesscontrol.StaticDomainAccessControlProvisioning;
import io.joynr.accesscontrol.StaticDomainAccessControlProvisioningModule;
import io.joynr.exceptions.JoynrRuntimeException;
import io.joynr.messaging.MessagingPropertyKeys;
import io.joynr.messaging.websocket.WebsocketModule;
import io.joynr.runtime.AbstractJoynrApplication;
import io.joynr.runtime.JoynrApplication;
import io.joynr.runtime.JoynrApplicationModule;
import io.joynr.runtime.JoynrInjectorFactory;
import io.joynr.runtime.LibjoynrWebSocketRuntimeModule;

import java.util.Properties;

import joynr.infrastructure.DacTypes.MasterAccessControlEntry;
import joynr.infrastructure.DacTypes.Permission;
import joynr.infrastructure.DacTypes.TrustLevel;
import joynr.types.ProviderScope;
import joynr.types.ProviderQos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.google.inject.Inject;
import com.google.inject.Module;

import edu.umd.cs.findbugs.annotations.SuppressWarnings;

public class MyRadioProviderApplication extends AbstractJoynrApplication {
    private static final Logger LOG = LoggerFactory.getLogger(MyRadioProviderApplication.class);
    public static final String STATIC_PERSISTENCE_FILE = "provider-joynr.properties";

    private MyRadioProvider provider = null;

    @Inject
    private ObjectMapper jsonSerializer;

    @Inject
    private ProviderScope providerScope;

    public static void main(String[] args) throws Exception {
        ProviderScope tmpProviderScope = ProviderScope.GLOBAL;
        String host = "localhost";
        int port = 4242;
        String localDomain = args[0];

        Properties joynrConfig = new Properties();
        configureWebSocket(host, port, joynrConfig);
        Module runtimeModule = new LibjoynrWebSocketRuntimeModule();
        final ProviderScope providerScope = tmpProviderScope;
        LOG.info("Using the following runtime module: " + runtimeModule.getClass().getSimpleName());
        LOG.info("Registering provider with the following scope: " + providerScope.name());
        LOG.info("Registering provider on domain \"{}\"", localDomain);

        // joynr config properties are used to set joynr configuration at
        // compile time. They are set on the
        // JoynrInjectorFactory.
        // Set a custom static persistence file (default is joynr.properties in
        // the working dir) to store
        // joynr configuration. It allows for changing the joynr configuration
        // at runtime. Custom persistence
        // files support running the consumer and provider applications from
        // within the same directory.
        joynrConfig.setProperty(MessagingPropertyKeys.PERSISTENCE_FILE, STATIC_PERSISTENCE_FILE);

        // How to use custom infrastructure elements:

        // 1) Set them programmatically at compile time using the joynr
        // configuration properties at the
        // JoynInjectorFactory. E.g. uncomment the following lines to set a
        // certain joynr server
        // instance.
        // joynrConfig.setProperty(MessagingPropertyKeys.BOUNCE_PROXY_URL,
        // "http://localhost:8080/bounceproxy/");
        // joynrConfig.setProperty(MessagingPropertyKeys.DISCOVERYDIRECTORYURL,
        // "http://localhost:8080/discovery/channels/discoverydirectory_channelid/");

        // Each joynr instance has a local domain. It identifies the execution
        // device/platform, e.g. the
        // vehicle. Normally, providers on that instance are registered for the
        // local domain.
        joynrConfig.setProperty(PROPERTY_JOYNR_DOMAIN_LOCAL, localDomain);

        // 2) Or set them in the static persistence file (default:
        // joynr.properties in working dir) at
        // runtime. If not available in the working dir, it will be created
        // during the first launch
        // of the application. Copy the following lines to the custom
        // persistence file to set a
        // certain joynr server instance.
        // NOTE: This application uses a custom static persistence file
        // provider-joynr.properties.
        // Copy the following lines to the custom persistence file to set a
        // certain joynr server
        // instance.
        // joynr.messaging.bounceproxyurl=http://localhost:8080/bounceproxy/
        // joynr.messaging.discoverydirectoryurl=http://localhost:8080/discovery/channels/discoverydirectory_channelid/

        // 3) Or set them in Java System properties.
        // -Djoynr.messaging.bounceProxyUrl=http://localhost:8080/bounceproxy/
        // -Djoynr.messaging.capabilitiesDirectoryUrl=http://localhost:8080/discovery/channels/discoverydirectory_channelid/

        // NOTE:
        // Programmatically set configuration properties override properties set
        // in the static persistence file.
        // Java system properties override both

        // Application-specific configuration properties are injected to the
        // application by setting
        // them on the JoynApplicationModule.
        Properties appConfig = new Properties();

        // Use injected static provisioning of access control entries to allow access to anyone to this interface
        provisionAccessControl(joynrConfig, localDomain);
        JoynrApplication joynrApplication = new JoynrInjectorFactory(joynrConfig,
                                                                     runtimeModule,
                                                                     new StaticDomainAccessControlProvisioningModule()).createApplication(new JoynrApplicationModule(MyRadioProviderApplication.class,
                                                                                                                                                                     appConfig) {
            @Override
            protected void configure() {
                super.configure();
                bind(ProviderScope.class).toInstance(providerScope);
            }
        });
        joynrApplication.run();

        joynrApplication.shutdown();
    }

    private static void configureWebSocket(String host, int port, Properties joynrConfig) {
        joynrConfig.setProperty(WebsocketModule.PROPERTY_WEBSOCKET_MESSAGING_HOST, host);
        joynrConfig.setProperty(WebsocketModule.PROPERTY_WEBSOCKET_MESSAGING_PORT, "" + port);
        joynrConfig.setProperty(WebsocketModule.PROPERTY_WEBSOCKET_MESSAGING_PROTOCOL, "ws");
        joynrConfig.setProperty(WebsocketModule.PROPERTY_WEBSOCKET_MESSAGING_PATH, "");
    }

    @Override
    public void run() {
        provider = new MyRadioProvider();
        provider.addBroadcastFilter(new TrafficServiceBroadcastFilter());
        provider.addBroadcastFilter(new GeocastBroadcastFilter(jsonSerializer));
        ProviderQos providerQos = new ProviderQos();
        providerQos.setPriority(System.currentTimeMillis());
        providerQos.setScope(providerScope);
        runtime.registerProvider(localDomain, provider, providerQos);
    }

    @Override
    @SuppressWarnings(value = "DM_EXIT", justification = "WORKAROUND to be removed")
    public void shutdown() {
        LOG.info("shutting down");
        if (provider != null) {
            try {
                runtime.unregisterProvider(localDomain, provider);
            } catch (JoynrRuntimeException e) {
                LOG.error("unable to unregister capabilities {}", e.getMessage());
            }
        }
        runtime.shutdown(true);
        // TODO currently there is a bug preventing all threads being stopped
        // WORKAROUND
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // do nothing; exiting application
        }
        System.exit(0);
    }

    private static void provisionAccessControl(Properties properties, String domain) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enableDefaultTypingAsProperty(DefaultTyping.JAVA_LANG_OBJECT, "_typeName");
        MasterAccessControlEntry newMasterAccessControlEntry = new MasterAccessControlEntry("*",
                                                                                            domain,
                                                                                            ProviderAnnotations.getInterfaceName(MyRadioProvider.class),
                                                                                            TrustLevel.LOW,
                                                                                            new TrustLevel[]{ TrustLevel.LOW },
                                                                                            TrustLevel.LOW,
                                                                                            new TrustLevel[]{ TrustLevel.LOW },
                                                                                            "*",
                                                                                            Permission.YES,
                                                                                            new Permission[]{ Permission.YES });

        MasterAccessControlEntry[] provisionedAccessControlEntries = { newMasterAccessControlEntry };
        String provisionedAccessControlEntriesAsJson = objectMapper.writeValueAsString(provisionedAccessControlEntries);
        properties.setProperty(StaticDomainAccessControlProvisioning.PROPERTY_PROVISIONED_MASTER_ACCESSCONTROLENTRIES,
                               provisionedAccessControlEntriesAsJson);
    }
}
