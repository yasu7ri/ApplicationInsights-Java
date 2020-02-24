package com.microsoft.applicationinsights.agent.internal.diagnostics;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class SdkVersionFinder extends CachedDiagnosticsValueFinder {

    @Override
    public String getName() {
        return "sdkVersion";
    }

    @Override
    protected String populateValue() {
        Properties props = new Properties();
        InputStream in = ClassLoader.getSystemClassLoader().getResourceAsStream("sdk-version.properties");
        if (in != null) {
            try {
                props.load(in);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }
}
