package de.schildbach.pte.live;

import de.schildbach.pte.LocationUtils;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.PtvProvider;
import de.schildbach.pte.dto.*;
import okhttp3.HttpUrl;
import org.junit.Test;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PtvProviderLiveTest extends AbstractProviderLiveTest {

    private final PtvProvider mockPtvProvider = new PtvProvider("135", "abcdefg");

    public PtvProviderLiveTest() {
        super(new PtvProvider(secretProperty("devid"), secretProperty("apikey")));
    }

    @Test
    public void appendSignatures() {
        HttpUrl urlWithoutQueryParam = HttpUrl.parse("https://test.example.com");
        assertEquals(
                "Should append devid, and then calculate and append signature",
                "https://test.example.com/?devid=135&signature=b8714bdbb8c309125b6b4e3aab70cf035d211022",
                mockPtvProvider.appendSignatureToUrl(urlWithoutQueryParam).toString()
        );

        HttpUrl urlWithQueryParam = HttpUrl.parse("https://test.example.com?queryParam=BLAH");
        assertEquals(
                "Should retain custom query parameters when appending devid and signature",
                "https://test.example.com/?queryParam=BLAH&devid=135&signature=28331dd78df59534b7be41e675d2840cc974250f",
                mockPtvProvider.appendSignatureToUrl(urlWithQueryParam).toString()
        );
    }

    @Test
    public void querySomeNearbyLocations() throws IOException {
        assertEquals(1, queryNearbyLocations(0, 1).size());
        assertEquals(3, queryNearbyLocations(0, 3).size());
        assertEquals(5, queryNearbyLocations(0, 5).size());
    }

    @Test
    public void queryLocationsWithDecreasingDistance() throws IOException {
        List<Location> locationsWithin1000 = queryNearbyLocations(1000, 0);
        List<Location> locationsWithin750 = queryNearbyLocations(750, 0);
        List<Location> locationsWithin500 = queryNearbyLocations(500, 0);
        List<Location> locationsWithin300 = queryNearbyLocations(300, 0);

        assertTrue(locationsWithin1000.size() > locationsWithin750.size());
        assertTrue(locationsWithin750.size() > locationsWithin500.size());
        assertTrue(locationsWithin500.size() > locationsWithin300.size());

        assertDistancesBelow(locationsWithin300, 300);
        assertDistancesBelow(locationsWithin500, 500);
        assertDistancesBelow(locationsWithin750, 750);
        assertDistancesBelow(locationsWithin1000, 1000);
    }

    private void assertDistancesBelow(List<Location> locations, int distance) {
        assertTrue(locations.size() > 0);
        for (Location location : locations) {
            float actualDistance = LocationUtils.computeDistance(location.getLatAsDouble(), location.getLonAsDouble(), MY_LAT, MY_LON);
            assertTrue(actualDistance < distance);
        }
    }

    @Test
    public void queryAllNearbyLocations() throws IOException {
        List<Location> locations = queryNearbyLocations(0, 0);
        assertTrue(locations.size() > 0);
    }

    private static final double MY_LAT = -37.817993;
    private static final double MY_LON = 144.981916;

    private List<Location> queryNearbyLocations(int maxDistance, int maxStations) throws IOException {
        NearbyLocationsResult response = queryNearbyLocations(
                EnumSet.allOf(LocationType.class),
                new Location(LocationType.STATION, "1", (int) (MY_LAT * 1E6), (int) (MY_LON * 1E6)),
                maxDistance,
                maxStations
        );

        assertNotNull(response);
        assertNotNull(response.header);
        assertEquals(response.header.network, NetworkId.MELBOURNE);
        assertNotNull(response.locations);

        return response.locations;
    }

    @Test
    public void suggestLocations() throws IOException {
        SuggestLocationsResult locations = provider.suggestLocations("Flinders Street");

        assertNotNull(locations);
        assertTrue(locations.getLocations().size() > 0);

        assertNotNull(locations.header);
        assertEquals(locations.header.network, NetworkId.MELBOURNE);
        assertEquals(locations.status, SuggestLocationsResult.Status.OK);

        // Flinders Street is the main train station in Melbourne. If we don't find it, something is wrong with this API.
        boolean foundFlindersStreetStation = false;
        for (Location location : locations.getLocations()) {
            assertNotNull(location.products);
            assertNotNull(location.id);

            // There are two types of services running out of Flinders Street Station: Regional and Suburban trains.
            // TODO: These two products should still get combined into the same Location (with two Products)
            if (location.products.contains(Product.SUBURBAN_TRAIN) && location.id.equals("1071")) {
                System.out.println(location.toString());
                assertEquals(location.name, "Flinders Street Station");
                assertEquals(location.type, LocationType.STATION);
                assertNotNull(location.products);
                assertTrue(location.products.contains(Product.SUBURBAN_TRAIN));

                foundFlindersStreetStation = true;
            }
        }

        assertTrue(foundFlindersStreetStation);
    }

}
