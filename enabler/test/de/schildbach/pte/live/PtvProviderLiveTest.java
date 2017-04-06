package de.schildbach.pte.live;

import de.schildbach.pte.PtvProvider;
import de.schildbach.pte.dto.SuggestLocationsResult;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class PtvProviderLiveTest extends AbstractProviderLiveTest {
    public PtvProviderLiveTest() {
        super(new PtvProvider());
    }

    @Test
    public void suggestLocations() throws Exception {
        final SuggestLocationsResult result = suggestLocations("Belgrave");
        assertNotNull(result);
        print(result);
    }
}
