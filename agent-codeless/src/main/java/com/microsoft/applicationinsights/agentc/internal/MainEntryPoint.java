/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.microsoft.applicationinsights.agentc.internal;

import java.io.File;
import java.io.FileInputStream;
import java.lang.instrument.Instrumentation;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.agentc.internal.model.Global;
import com.microsoft.applicationinsights.internal.config.ApplicationInsightsXmlConfiguration;
import com.microsoft.applicationinsights.internal.config.SDKLoggerXmlElement;
import com.microsoft.applicationinsights.internal.config.TelemetryConfigurationFactory;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glowroot.instrumentation.engine.config.InstrumentationDescriptor;
import org.glowroot.instrumentation.engine.config.InstrumentationDescriptors;
import org.glowroot.instrumentation.engine.impl.InstrumentationServiceImpl.ConfigServiceFactory;
import org.glowroot.instrumentation.engine.impl.SimpleConfigServiceFactory;
import org.glowroot.instrumentation.engine.init.EngineModule;
import org.glowroot.instrumentation.engine.init.MainEntryPointUtil;
import org.slf4j.Logger;

public class MainEntryPoint {

    private static @Nullable Logger startupLogger;

    private MainEntryPoint() {
    }

    public static void premain(Instrumentation instrumentation, File agentJarFile) {
        try {
            startupLogger = initLogging(instrumentation, agentJarFile);
            addLibJars(instrumentation, agentJarFile);
            instrumentation.addTransformer(new CommonsLogFactoryClassFileTransformer());
            start(instrumentation, agentJarFile);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable t) {
            startupLogger.error("Agent failed to start.", t);
            t.printStackTrace();
        }
    }

    public static Logger initLogging(Instrumentation instrumentation, File agentJarFile) {
        File logbackXmlOverride = new File(agentJarFile.getParentFile(), "ai.logback.xml");
        if (logbackXmlOverride.exists()) {
            System.setProperty("ai.logback.configurationFile", logbackXmlOverride.getAbsolutePath());
        }
        try {
            return MainEntryPointUtil.initLogging("com.microsoft.applicationinsights", instrumentation);
        } finally {
            System.clearProperty("ai.logback.configurationFile");
        }
    }

    private static void addLibJars(Instrumentation instrumentation, File agentJarFile) throws Exception {
        File libDir = new File(agentJarFile.getParentFile(), "lib");
        if (!libDir.exists()) {
            return;
        }
        File[] files = libDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.getName().endsWith(".jar")) {
                instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(file));
            }
        }
    }

    private static void start(Instrumentation instrumentation, File agentJarFile) throws Exception {

        File agentJarParentFile = agentJarFile.getParentFile();
        File javaTmpDir = new File(System.getProperty("java.io.tmpdir"));
        File tmpDir = new File(javaTmpDir, "appinsights-java");
        if (!tmpDir.exists() && !tmpDir.mkdirs()) {
            throw new Exception("Could not create directory: " + tmpDir.getAbsolutePath());
        }

        Properties props = new Properties();
        File propertiesFile = new File(agentJarParentFile, "ApplicationInsights.properties");
        if (propertiesFile.exists()) {
            try (FileInputStream in = new FileInputStream(propertiesFile)) {
                props.load(in);
            }
        }

        // TODO add these properties, what should default be in 3.0 / codeless??

        // Global.setOutboundW3CEnabled(w3cEnabled);
        // Global.setInboundW3CEnabled(w3cEnabled);

        // Global.setOutboundW3CBackCompatEnabled(w3cBackCompatEnabled);
        // TraceContextCorrelationCore.setIsW3CBackCompatEnabled(w3cBackCompatEnabled);

        ApplicationInsightsXmlConfiguration xmlConfiguration = new ApplicationInsightsXmlConfiguration();

        String connectionString = getProperty("appInsights.connectionString", props);
        Connection connection = parseConnectionString(connectionString);
        if (!Strings.isNullOrEmpty(connection.instrumentationKey)) {
            xmlConfiguration.setInstrumentationKey(connection.instrumentationKey);
        }
        if (!Strings.isNullOrEmpty(connection.ingestionEndpoint)) {
            xmlConfiguration.getChannel().setEndpointAddress(connection.ingestionEndpoint + "v2/track");
        }
        String roleName = getProperty("appInsights.roleName", props);
        if (!Strings.isNullOrEmpty(roleName)) {
            xmlConfiguration.setRoleName(roleName);
        }

        if (Boolean.valueOf(getProperty("appInsights.debug", props))) {
            SDKLoggerXmlElement sdkLogger = new SDKLoggerXmlElement();
            sdkLogger.setType("CONSOLE");
            sdkLogger.setLevel("TRACE");
            xmlConfiguration.setSdkLogger(sdkLogger);
            xmlConfiguration.getChannel().setDeveloperMode(true);
        }

        List<InstrumentationDescriptor> instrumentationDescriptors = InstrumentationDescriptors.read();

        ConfigServiceFactory configServiceFactory = new SimpleConfigServiceFactory(instrumentationDescriptors,
                Configuration.getInstrumentationConfig(props));

        // need to add instrumentation transformers before initializing telemetry configuration below, since that starts
        // some threads and loads some classes that the instrumentation wants to weave
        EngineModule.createWithSomeDefaults(instrumentation, tmpDir, Global.getThreadContextThreadLocal(),
                instrumentationDescriptors, configServiceFactory, new AgentImpl(),
                Collections.singletonList("com.microsoft.applicationinsights.agentc"), agentJarFile);

        TelemetryConfiguration configuration = TelemetryConfiguration.getActiveWithoutInitializingConfig();
        TelemetryConfigurationFactory.INSTANCE.initialize(configuration, xmlConfiguration);
        // important to set TelemetryClient before doing any instrumentation, so we can guarantee
        // Global.getTelemetryClient() always returns non-null
        Global.setTelemetryClient(new TelemetryClient());
    }

    private static @Nullable String getProperty(String propertyName, Properties props) {
        String envVariableName = propertyName.replace('.', '_').toUpperCase(Locale.ENGLISH);
        String propertyValue = System.getenv(envVariableName);
        if (!Strings.isNullOrEmpty(propertyValue)) {
            return propertyValue;
        }
        propertyValue = System.getProperty(propertyName);
        if (!Strings.isNullOrEmpty(propertyValue)) {
            return propertyValue;
        }
        propertyValue = props.getProperty(propertyName);
        if (!Strings.isNullOrEmpty(propertyValue)) {
            return propertyValue;
        }
        return null;
    }

    private static Connection parseConnectionString(String connectionString) {
        Connection connection = new Connection();
        if (connectionString != null) {
            List<String> parts = Splitter.on(';').splitToList(connectionString);
            Map<String, String> map = Maps.newHashMap();
            for (String part : parts) {
                int index = part.indexOf('=');
                map.put(part.substring(0, index).toLowerCase(Locale.ENGLISH), part.substring(index + 1));
            }
            String instrumentationKey = map.get("instrumentationkey");
            if (!Strings.isNullOrEmpty(instrumentationKey)) {
                connection.instrumentationKey = instrumentationKey;
            }
            String ingestionEndpoint = map.get("ingestionendpoint");
            if (!Strings.isNullOrEmpty(ingestionEndpoint)) {
                if (!ingestionEndpoint.endsWith("/")) {
                    // TODO check with Mikhail if this leniency is needed
                    ingestionEndpoint += "/";
                }
                connection.ingestionEndpoint = ingestionEndpoint;
            }
        }
        return connection;
    }

    private static class Connection {
        private @Nullable String instrumentationKey;
        private String ingestionEndpoint = "https://dc.services.visualstudio.com/";
    }
}
