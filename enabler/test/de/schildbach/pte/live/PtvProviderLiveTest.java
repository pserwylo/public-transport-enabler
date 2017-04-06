package de.schildbach.pte.live;

import de.schildbach.pte.PtvProvider;
import de.schildbach.pte.dto.SuggestLocationsResult;
import okhttp3.HttpUrl;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PtvProviderLiveTest extends AbstractProviderLiveTest {
    public PtvProviderLiveTest() {
        super(new PtvProvider());
    }

    @Test
    public void appendSignatures() {
        String privateKey = "";

        HttpUrl urlWithoutQueryParam = HttpUrl.parse("https://test.example.com");
        assertEquals(
                "https://test.example.com?devid=135&signature=",
                PtvProvider.appendSignatureToUrl(urlWithoutQueryParam, 135, privateKey).toString()
        );

        HttpUrl urlWithQueryParam = HttpUrl.parse("https://test.example.com?queryParam=BLAH");
        assertEquals(
                "https://test.example.com?queryParam=BLAH&devid=135&signature=",
                PtvProvider.appendSignatureToUrl(urlWithQueryParam, 135, privateKey).toString()
        );
    }
}
