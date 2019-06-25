package com.microsoft.applicationinsights;

import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;

public class EndpointConfiguration {
    private static final String DEFAULT_TELEMETRY_ENDPOINT_HOST_URL = "https://dc.services.visualstudio.com";
    private static final String DEFAULT_QUICKPULSE_ENDPOINT_HOST_URL = "https://rt.services.visualstudio.com";
    private static final String DEFAULT_TELEMETRY_ENDPOINT_URI_PATH = "/v2/track";
    private static final String DEFAULT_QUICKPULSE_ENDPOINT_URI_PATH = "/QuickPulseService.svc";
    public static final String DEFAULT_PROFILE_QUERY_ENDPOINT_URI_PATH_FORMAT = "/api/profiles/%s/appId";

    public static final String DEFAULT_TELEMETRY_ENDPOINT = DEFAULT_TELEMETRY_ENDPOINT_HOST_URL + DEFAULT_TELEMETRY_ENDPOINT_URI_PATH;
    public static final String DEFAULT_QUICKPULSE_ENDPOINT = DEFAULT_QUICKPULSE_ENDPOINT_HOST_URL + DEFAULT_QUICKPULSE_ENDPOINT_URI_PATH;
    public static final String DEFAULT_PROFILE_QUERY_ENDPOINT_FORMAT = DEFAULT_TELEMETRY_ENDPOINT_HOST_URL + DEFAULT_PROFILE_QUERY_ENDPOINT_URI_PATH_FORMAT;

    private URL telemetryEndpointHostUrl;
    private String telemetryEndpoint = DEFAULT_TELEMETRY_ENDPOINT;
    private String quickPulseEndpoint = DEFAULT_QUICKPULSE_ENDPOINT;
    private String profileQueryEndpointFormat = null;

    public EndpointConfiguration() {
        telemetryEndpointHostUrl = validateArgToUrl("DEFAULT_TELEMETRY_ENDPOINT_HOST_URL", DEFAULT_TELEMETRY_ENDPOINT_HOST_URL);
    }

    public String getTelemetryEndpoint() {
        return telemetryEndpoint;
    }

    public void setTelemetryEndpoint(String telemetryEndpoint) {
        URL url = validateArgToUrl("telemetryEndpoint", telemetryEndpoint);
        try {
            this.telemetryEndpointHostUrl = new URL(url.getProtocol(), url.getHost(), url.getPort(), "");
        } catch (MalformedURLException e) {
            // nop
        }
        this.telemetryEndpoint = url.toString();
    }

    private URL validateArgToUrl(String varName, String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(varName+" cannot be blank");
        }
        if (!(StringUtils.startsWith("http://", value) && StringUtils.startsWith("https://", value))) {
            throw new IllegalArgumentException(varName + "must start with http:// or https://");
        }
        try {
            return new URL(value);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(varName +" is not a valid URL", e);
        }
    }


    public String getQuickPulseEndpoint() {
        return quickPulseEndpoint;
    }

    public void setQuickPulseEndpoint(String quickPulseEndpoint) {
        this.quickPulseEndpoint = validateArgToUrl("quickPulseEndpoint", quickPulseEndpoint).toString();
    }

    public String getProfileQueryEndpointFormat() {
        if (profileQueryEndpointFormat != null) {
            return profileQueryEndpointFormat;
        }

        try {
            return new URL(telemetryEndpointHostUrl.getProtocol(), telemetryEndpointHostUrl.getHost(), telemetryEndpointHostUrl.getPort(),
                    DEFAULT_PROFILE_QUERY_ENDPOINT_URI_PATH_FORMAT).toString();
        } catch (MalformedURLException e) {
            throw new IllegalStateException(telemetryEndpointHostUrl.toString()+" + "+DEFAULT_PROFILE_QUERY_ENDPOINT_URI_PATH_FORMAT+" did not make a valid url");
        }
    }

    /**
     * Add %s where ikey should go
     * @param profileQueryEndpointFormat the url format string for the profile query
     */
    public void setProfileQueryEndpointFormat(String profileQueryEndpointFormat) {
        this.profileQueryEndpointFormat = validateArgToUrl("profileQueryEndpointFormat", profileQueryEndpointFormat).toString();
    }
}
