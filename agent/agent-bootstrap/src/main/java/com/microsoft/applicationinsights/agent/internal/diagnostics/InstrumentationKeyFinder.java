package com.microsoft.applicationinsights.agent.internal.diagnostics;

public class InstrumentationKeyFinder implements DiagnosticsValueFinder {

    private static final String PREFIX = "InstrumentationKey=";

    @Override
    public String getName() {
        return "ikey";
    }

    @Override
    public String getValue() {
        final String connStr = System.getenv("APPLICATIONINSIGHTS_CONNECTION_STRING");
        if (connStr != null && !connStr.isEmpty()) {
            String[] parts = connStr.split(";");
            String instrumentationKey = null;
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
                    instrumentationKey = trimmed.substring(PREFIX.length());
                }
            }
            // return the last instrumentation key to match ConnectionString::parseInto
            return instrumentationKey;
        }
        return System.getenv("APPINSIGHTS_INSTRUMENTATIONKEY");
    }
}
