package com.microsoft.applicationinsights.extensibility.initializer;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder.ListMultimapBuilder;
import com.microsoft.applicationinsights.extensibility.TelemetryInitializer;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import com.microsoft.applicationinsights.telemetry.Telemetry;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RequestAggregatorTelemetryInitializer implements TelemetryInitializer {

    class UriParameterDescriptor {
        private String name;
        private int group;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getGroup() {
            return group;
        }

        public void setGroup(int group) {
            this.group = group;
        }
    }

    class UriFilterDescriptor {
        private String name;
        private String fullUriSpec;
        private Pattern regex;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFullUriSpec() {
            return fullUriSpec;
        }

        public void setFullUriSpec(String fullUriSpec) {
            this.fullUriSpec = fullUriSpec;
        }

        public Pattern getRegex() {
            return regex;
        }

        public void setRegex(Pattern regex) {
            this.regex = regex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UriFilterDescriptor that = (UriFilterDescriptor) o;
            return Objects.equals(getName(), that.getName()) &&
                    Objects.equals(getFullUriSpec(), that.getFullUriSpec());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getName(), getFullUriSpec());
        }
    }

    protected Multimap<UriFilterDescriptor, UriParameterDescriptor> patternMap = ListMultimapBuilder
            .linkedHashKeys()
            .arrayListValues()
            .build();

    public RequestAggregatorTelemetryInitializer() {
        this(null);
    }

    private static Pattern parameterExtracterPattern = Pattern.compile("\\{(.*?)\\}");

    public RequestAggregatorTelemetryInitializer(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException(RequestAggregatorTelemetryInitializer.class.getSimpleName()+": You must specify at least one parameter");
        }

        for(Entry<String, String> entry : data.entrySet()) {
            UriFilterDescriptor descriptor = new UriFilterDescriptor();
            descriptor.setName(entry.getKey());
            String spec = entry.getValue();
            descriptor.setFullUriSpec(spec);
            // does updating spec change the matcher's reference?
            Matcher specMatcher = parameterExtracterPattern.matcher(new String(spec));
            for (int i = 1; i < specMatcher.groupCount(); i++) {
                UriParameterDescriptor paramSpec = new UriParameterDescriptor();
                final String name = specMatcher.group(i);
                paramSpec.setName(name);
                paramSpec.setGroup(i);
                patternMap.put(descriptor, paramSpec);
                spec = spec.replaceFirst(String.format("{%s}", name), "(.*?)");
            }
            spec = String.format(".*%s.*", spec);
            descriptor.setRegex(Pattern.compile(spec));
        }
    }

    String generateUriParameterExtractionRegex(String uriSpec, List<UriParameterDescriptor> params) {
        Matcher specMatcher = parameterExtracterPattern.matcher(uriSpec);
        StringBuffer regexBuffer = new StringBuffer();
        for (int i = 1; specMatcher.find(); i++) {
            UriParameterDescriptor paramSpec = new UriParameterDescriptor();
            String paramName = specMatcher.group(1);
            paramSpec.setName(paramName);
            paramSpec.setGroup(1);
            params.add(paramSpec);
            specMatcher.appendReplacement(regexBuffer, Matcher.quoteReplacement("(.*?)"));
        }
        specMatcher.appendTail(regexBuffer);
        return regexBuffer.append(".*").insert(0, ".*").toString().replaceAll("\\/", Matcher.quoteReplacement("\\/"));

    }



    @Override
    public void initialize(Telemetry telemetry) {
        if (!(telemetry instanceof RequestTelemetry)) {
            return;
        }

        RequestTelemetry rt = (RequestTelemetry) telemetry;
        String url = rt.getUrlString();
        for (UriFilterDescriptor ufd : patternMap.keySet()) {
            Matcher m = ufd.getRegex().matcher(url);
            if (m.matches()) {
                for (UriParameterDescriptor upd : patternMap.get(ufd)) {
                    String value = m.group(upd.getGroup());
                    rt.getProperties().put(upd.getName(), value);

                }
            }
        }
    }
}
