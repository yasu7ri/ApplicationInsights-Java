package com.microsoft.applicationinsights;

import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;

public class EndpointConfiguration {
    public static final String DEFAULT_TELEMETRY_ENDPOINT_HOST_URL = "https://dc.services.visualstudio.com";
    public static final String DEFAULT_QUICKPULSE_ENDPOINT_HOST_URL = "https://rt.services.visualstudio.com";
    public static final String DEFAULT_TELEMETRY_ENDPOINT_URI_PATH = "/v2/track";
    public static final String DEFAULT_QUICKPULSE_ENDPOINT_URI_PATH = "/QuickPulseService.svc";
    public static final String DEFAULT_PROFILE_QUERY_ENDPOINT_URI_PATH_FORMAT = "/api/profiles/%s/appId";

    public static final String DEFAULT_TELEMETRY_ENDPOINT = DEFAULT_TELEMETRY_ENDPOINT_HOST_URL + DEFAULT_TELEMETRY_ENDPOINT_URI_PATH;
    public static final String DEFAULT_QUICKPULSE_ENDPOINT = DEFAULT_QUICKPULSE_ENDPOINT_HOST_URL + DEFAULT_QUICKPULSE_ENDPOINT_URI_PATH;
    public static final String DEFAULT_PROFILE_QUERY_ENDPOINT = DEFAULT_TELEMETRY_ENDPOINT_HOST_URL + DEFAULT_PROFILE_QUERY_ENDPOINT_URI_PATH_FORMAT;
    // TODO CDS endpoint
    private String telemetryEndpointHostUrl = DEFAULT_TELEMETRY_ENDPOINT_HOST_URL;
    private String telemetryEndpointUriPath = DEFAULT_TELEMETRY_ENDPOINT_URI_PATH;
    private String quickPulseEndpointHostUrl = DEFAULT_QUICKPULSE_ENDPOINT_HOST_URL;
    private String quickPluseEndpointUriPath = DEFAULT_QUICKPULSE_ENDPOINT_URI_PATH;
    private String profileQueryEndpointHostUrl = DEFAULT_TELEMETRY_ENDPOINT;
    private String profileQueryEndpointUriPathFormat = DEFAULT_PROFILE_QUERY_ENDPOINT_URI_PATH_FORMAT;

    public String getTelemetryEndpoint() {
        return telemetryEndpointHostUrl + telemetryEndpointUriPath;
    }

    public void setTelemetryEndpointHostUrl(String telemetryEndpointHostUrl) {
        validateUrl("telemetryEnpointHostUrl", telemetryEndpointHostUrl);
        this.telemetryEndpointHostUrl = cleanHostValue(telemetryEndpointHostUrl);
    }

    private String cleanHostValue(String telemetryEndpointHostUrl) {
        return StringUtils.stripEnd(telemetryEndpointHostUrl, "/");
    }

    private void validateUrl(String varName, String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(varName+" cannot be blank");
        }
        if (!(StringUtils.startsWith("http://", value) && StringUtils.startsWith("https://", value))) {
            throw new IllegalArgumentException(varName + "must start with http:// or https://");
        }
        try {
            URL url = new URL(value);

        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(varName+" is not a valid URL", e);
        }
    }

    public void setTelemetryEndpointUriPath(String telemetryEndpointUriPath) {
        validateUriPath("telemetryEndpointUriPath", telemetryEndpointUriPath);
        this.telemetryEndpointUriPath = cleanUriPathValue(telemetryEndpointUriPath);
    }

    private void validateUriPath(String varName, String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(varName+" cannot be blank");
        }
    }

    private String cleanUriPathValue(String value) {
        return "/" + StringUtils.stripStart(value, "/");
    }

    public String getQuickPulseEndpoint() {
        return quickPulseEndpointHostUrl + quickPluseEndpointUriPath;
    }

    public void setQuickPulseEndpointHostUrl(String quickPulseEndpointHostUrl) {
        validateUrl("quickPulseendpoingHostUrl", quickPulseEndpointHostUrl);
        this.quickPulseEndpointHostUrl = cleanHostValue(quickPulseEndpointHostUrl);
    }

    public void setDefaultQuickpulseEndpointUriPath(String quickPulseEndpointUriPath) {
        validateUriPath("quickPulseEndpiontUriPath", quickPulseEndpointUriPath);
        this.quickPluseEndpointUriPath = cleanUriPathValue(quickPulseEndpointUriPath);
    }

    public String getProfileQueryEndpointFormat() {
        return profileQueryEndpointHostUrl + profileQueryEndpointUriPathFormat;
    }

    public void setProfileQueryEndpointHostUrl(String profileQueryEndpointHostUrl) {
        validateUrl("profileQueryEndpointHostUrl", profileQueryEndpointHostUrl);
        this.profileQueryEndpointHostUrl = cleanHostValue(profileQueryEndpointHostUrl);
    }

    public void setDefaultProfileQueryEndpoingUriPathFormat(String profileQueryEndpointUriPathFormat) {
        validateUriPath("profileQueryEndpointUriPathFormat", profileQueryEndpointUriPathFormat);
        this.profileQueryEndpointUriPathFormat = cleanUriPathValue(profileQueryEndpointUriPathFormat);
    }
}
