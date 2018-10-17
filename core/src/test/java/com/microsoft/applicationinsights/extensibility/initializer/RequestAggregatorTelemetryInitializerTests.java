package com.microsoft.applicationinsights.extensibility.initializer;


import com.google.common.collect.Multimap;
import com.microsoft.applicationinsights.extensibility.initializer.RequestAggregatorTelemetryInitializer.UriFilterDescriptor;
import com.microsoft.applicationinsights.extensibility.initializer.RequestAggregatorTelemetryInitializer.UriParameterDescriptor;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import org.hamcrest.Description;
import org.junit.*;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

public class RequestAggregatorTelemetryInitializerTests {

    @Test
    public void testFindReplace() {
        String spec = "/api/v1/sub/{subId}/resource/{resId}/create";
        Pattern p = Pattern.compile("[{](.*?)[}]");
        Matcher m = p.matcher(spec);
        StringBuffer sb = new StringBuffer();
        Map<Integer, String> names = new HashMap<Integer, String>();
        for (int i = 1; m.find(); i++) {
            final String paramName = m.group(1);
            System.out.printf("%d: %s%n", i, paramName);
            m.appendReplacement(sb, Matcher.quoteReplacement("(.*?)"));
            names.put(i, paramName);
        }
        m.appendTail(sb);
        sb.insert(0, ".*").append(".*");
        String urlPattern = sb.toString();
        urlPattern = urlPattern.replaceAll("\\/", Matcher.quoteReplacement("\\/"));
        System.out.println(urlPattern);

        String url = "http://api.host.com/api/v1/sub/1234-abcd-ef/resource/some-res/create?q=asdf";
        Pattern up = Pattern.compile(urlPattern);
        Matcher um = up.matcher(url);
        Assert.assertTrue("didn't match", um.matches());
        StringBuffer sb2 = new StringBuffer();
        int lastIndex = 0;
        for (Entry<Integer, String> entry : names.entrySet()) {
            final Integer gn = entry.getKey();
            String g = um.group(gn);
            final String pname = entry.getValue();
            System.out.printf("%d: %s = %s%n", gn, pname, g);
            sb2.append(url, lastIndex, um.start(gn))
                    .append('{')
                    .append(pname)
                    .append('}');
            lastIndex = um.end(gn);
        }
        sb2.append(url.substring(lastIndex));
        System.out.println(sb2.toString());
    }

    @Test
    public void generateUriParameterExtractionRegexReturnsRegexAndExtractsParameters() {
        String spec = "/api/v1/sub/{subId}/resource/{resId}/create";

        final ArrayList<UriParameterDescriptor> params = new ArrayList<>();
        final String result = RequestAggregatorTelemetryInitializer.generateUriParameterExtractionRegex(spec, params);

        System.out.println("result = "+result);
        assertEquals(".*\\/api\\/v1\\/sub\\/(.*?)\\/resource\\/(.*?)\\/create.*", result);
        assertThat(params, hasSize(2));
        assertThat(params, hasItem(new UriParameterDescriptor("subId", 1)));
        assertThat(params, hasItem(new UriParameterDescriptor("resId", 2)));
    }

    @Test
    public void configGeneneratesProperSpec() {
        Map<String, String> config = new HashMap<String, String>();
        final String specName = "testSpec1";
        final String specString = "/api/v1/sub/{subId}/resource/{resId}/create";
        config.put(specName, specString);
        RequestAggregatorTelemetryInitializer rati = new RequestAggregatorTelemetryInitializer(config);
        assertFalse(rati.patternMap.isEmpty());

        UriFilterDescriptor expectedDescriptor = new UriFilterDescriptor(specName, specString);
        assertTrue(rati.patternMap.containsKey(expectedDescriptor));
        final Collection<UriParameterDescriptor> params = rati.patternMap.get(expectedDescriptor);
        assertThat(params, hasItem(new UriParameterDescriptor("subId", 1)));
        assertThat(params, hasItem(new UriParameterDescriptor("resId", 2)));
    }

    @Test
    public void  initializerFuzzesName() throws Exception {
        Map<String, String> config = new HashMap<String, String>();
        final String specName = "testSpec1";
        final String specString = "/api/v1/sub/{subId}/resource/{resId}/create";
        config.put(specName, specString);
        RequestAggregatorTelemetryInitializer rati = new RequestAggregatorTelemetryInitializer(config);

        RequestTelemetry rt = new RequestTelemetry();
        rt.setUrl("http://api.host.com/api/v1/sub/1234-abcd-ef/resource/some-res/create?q=asdf");
        rt.setName("POST /api/v1/sub/1234-abcd-ef/resource/some-res/create");
        rati.initialize(rt);
        assertEquals("POST /api/v1/sub/{subId}/resource/{resId}/create", rt.getName());
    }
}
