package de.schildbach.pte;

import de.schildbach.pte.dto.*;

import java.io.IOException;
import java.util.*;

public class PtvProvider implements NetworkProvider {

    @Override
    public NetworkId id() {
        return NetworkId.MELBOURNE;
    }

    @Override
    public boolean hasCapabilities(Capability... capabilities) {
        return false;
    }

    @Override
    public NearbyLocationsResult queryNearbyLocations(EnumSet<LocationType> types, Location location, int maxDistance, int maxLocations) throws IOException {
        return null;
    }

    @Override
    public QueryDeparturesResult queryDepartures(String stationId, Date time, int maxDepartures, boolean equivs) throws IOException {
        return null;
    }

    @Override
    public SuggestLocationsResult suggestLocations(CharSequence constraint) throws IOException {
        return null;
    }

    @Override
    public Set<Product> defaultProducts() {
        return null;
    }

    @Override
    public QueryTripsResult queryTrips(Location from, Location via, Location to, Date date, boolean dep, Set<Product> products, Optimize optimize, WalkSpeed walkSpeed, Accessibility accessibility, Set<Option> options) throws IOException {
        return null;
    }

    @Override
    public QueryTripsResult queryMoreTrips(QueryTripsContext context, boolean later) throws IOException {
        return null;
    }

    @Override
    public Style lineStyle(String network, Product product, String label) {
        return null;
    }

    @Override
    public Point[] getArea() throws IOException {
        return new Point[0];
    }
}
