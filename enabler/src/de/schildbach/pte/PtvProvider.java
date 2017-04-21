package de.schildbach.pte;

import de.schildbach.pte.dto.*;
import de.schildbach.pte.util.HttpClient;
import de.schildbach.pte.util.ParserUtils;
import okhttp3.HttpUrl;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Implements the custom API provided by Public Transport Victoria (PTV) in Melbourne, Australia.
 *
 * There are both <a href="https://www.data.vic.gov.au/data/dataset//ptv-timetable-api">official docs</a>, and some
 * much more helpful <a href="">unofficial docs</a> provided by Steve Bennett (unaffiliated with PTV).
 */
public class PtvProvider extends AbstractNetworkProvider {

    private static final Logger LOG = LoggerFactory.getLogger(PtvProvider.class);

    private static final HttpUrl API_BASE_URL = new HttpUrl.Builder()
            .scheme("https")
            .host("timetableapi.ptv.vic.gov.au")
            .build();

    private String privateKey;
    private String developerId;

    public PtvProvider(String developerId, String privateKey) {
        super(NetworkId.MELBOURNE);
        this.developerId = developerId;
        this.privateKey = privateKey;
    }

    @Override
    public boolean hasCapability(Capability capability) {
        return true;
    }

    /**
     * Queries the /v2/nearme/latitude/%d/longitude/%d end point to get public transport stops which
     * are near {@param location}.
     * @see <a href="https://stevage.github.io/PTV-API-doc/#header15">API Docs</a>
     */
    @Override
    @Nonnull
    public NearbyLocationsResult queryNearbyLocations(EnumSet<LocationType> types, Location location, int maxDistance, int maxLocations) throws IOException {
        assertHealthCheck();

        final HttpUrl url = appendSignatureToUrl(
                API_BASE_URL.newBuilder()
                        .addPathSegments("v2/nearme/latitude")
                        .addPathSegment(Double.toString(location.getLatAsDouble()))
                        .addPathSegments("longitude")
                        .addPathSegment(Double.toString(location.getLonAsDouble()))
                        .build()
        );

        final CharSequence response = new HttpClient().get(url);
        try {
            final JSONArray array = new JSONArray(response.toString());
            final List<Location> locations = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i ++) {
                final JSONObject foundJsonLocation = array.getJSONObject(i).getJSONObject("result");
                final Location foundLocation = parseLocation(foundJsonLocation);
                if (maxDistance == 0 || foundJsonLocation.getDouble("distance") < maxDistance) {
                    locations.add(foundLocation);
                }

                if (maxLocations > 0 && locations.size() >= maxLocations) {
                    break;
                }
            }

            return new NearbyLocationsResult(new ResultHeader(id(), ""), locations);
        } catch (JSONException e) {
            throw new IOException("Error parsing JSON response", e);
        }
    }

    // TODO: What are equivs?
    @Override
    public QueryDeparturesResult queryDepartures(String stationId, Date time, int maxDepartures, boolean equivs) throws IOException {
        assertHealthCheck();

        final HttpUrl url = appendSignatureToUrl(
                API_BASE_URL.newBuilder()
                        .addPathSegments("v2/mode/0/stop")
                        .addPathSegment(stationId)
                        .addPathSegments("departures/by-destination")
                        .addPathSegment("limit")
                        .addPathSegment(maxDepartures > 0 ? Integer.toString(maxDepartures) : "0")
                        .build()
        );

        try {
            final CharSequence httpResponse = new HttpClient().get(url);
            final JSONObject jsonResponse = new JSONObject(httpResponse.toString());
            final JSONArray jsonValues = jsonResponse.getJSONArray("values");
            final QueryDeparturesResult result = new QueryDeparturesResult(new ResultHeader(id(), "v2"));

            List<Departure> departures = new ArrayList<>();
            List<LineDestination> lines = new ArrayList<>();
            for (int i = 0; i < jsonValues.length(); i ++) {
                final JSONObject value = jsonValues.getJSONObject(i);

                /*
                 * "platform": {
                 *   "platform_number": "3",
                 *   "at_platform_now": false,
                 *   "realtime_id": 0,
                 *   "stop": {
                 *     ...
                 *   },
                 *   "direction": {
                 *     ...
                 *   }
                 * },
                 */
                final JSONObject platform = value.getJSONObject("platform");

                /*
                 * "stop": {
                 *   "distance": 0.0,
                 *   "suburb": "Camberwell",
                 *   "transport_type": "train",
                 *   "route_type": 0,
                 *   "stop_id": 1032,
                 *   "location_name": "Camberwell",
                 *   "lat": -37.8265648,
                 *   "lon": 145.058685
                 * },
                 */
                final JSONObject stop = platform.getJSONObject("stop");

                /*
                 * "direction": {
                 *   "linedir_id": 34,
                 *   "direction_id": 0,
                 *   "direction_name": "Alamein",
                 *   "line": {
                 *     ...
                 *   }
                 * }
                 */
                final JSONObject direction = platform.getJSONObject("direction");

                /*
                 * "line": {
                 *   "transport_type": "train",
                 *   "route_type": 0,
                 *   "line_id": 1,
                 *   "line_name": "Alamein",
                 *   "line_number": "Alamein",
                 *   "line_name_short": "Alamein",
                 *   "line_number_long": ""
                 * }
                 */
                final JSONObject line = direction.getJSONObject("line");

                final Line ptvLine = new Line(
                        line.getString("line_id"),
                        "",
                        parseProductFromTransportType(line.getString("transport_type")),
                        line.getString("line_name")
                );

                final Location destination = new Location(
                        LocationType.STATION,
                        direction.getString("linedir_id")
                );

                departures.add(new Departure(
                        ParserUtils.parseIso8601DateTime(value.getString("time_timetable_utc")).getTime(),
                        value.isNull("time_realtime_utc") ? null : ParserUtils.parseIso8601DateTime(value.getString("time_realtime_utc")).getTime(),
                        ptvLine,
                        new Position(""),
                        destination,
                        new int[] {},
                        ""
                ));

                lines.add(new LineDestination(ptvLine, destination));
            }

            result.stationDepartures.add(new StationDepartures(
                    new Location(LocationType.STATION, stationId),
                    departures,
                    lines
            ));

            return result;

        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    /**
     * Queries the /v2/search endpoint to get public transport stops which match the {@param constraint}.
     * @see <a href="https://stevage.github.io/PTV-API-doc/#header17">API Docs</a>
     */
    @Override
    public SuggestLocationsResult suggestLocations(CharSequence constraint) throws IOException {
        assertHealthCheck();

        final HttpUrl url = appendSignatureToUrl(
                API_BASE_URL.newBuilder()
                        .addPathSegments("v2/search")
                        .addPathSegment(constraint.toString())
                        .build()
        );

        final CharSequence response = new HttpClient().get(url);
        try {
            final JSONArray jsonResponse = new JSONArray(response.toString());
            List<SuggestedLocation> locations = new ArrayList<>(jsonResponse.length());
            for (int i = 0; i < jsonResponse.length(); i ++) {
                final JSONObject object = jsonResponse.getJSONObject(i);
                final SuggestedLocation location = parseSuggestedLocation(object, i);

                if (location != null) {
                    locations.add(location);
                }
            }
            return new SuggestLocationsResult(new ResultHeader(id(), ""), locations);
        } catch (JSONException e) {
            throw new IOException("Error parsing JSON response", e);
        }
    }

    /**
     * Ideally this would be @Nonnull-able. But for future proofing this provider against future, currently unknown
     * attribute values we allow for it to return null.
     */
    @Nullable
    private SuggestedLocation parseSuggestedLocation(JSONObject object, int index) throws JSONException {
        final Location location = locationFromJSON(object);
        return location != null ? new SuggestedLocation(locationFromJSON(object), index) : null;
    }

    /**
     * Ideally this would be @Nonnull-able. But for future proofing this provider against future, currently unknown
     * attribute values we allow for it to return null.
     */
    @Nullable
    private Location locationFromJSON(JSONObject object) throws JSONException {
        final JSONObject result = object.getJSONObject("result");
        switch (object.getString("type")) {
            case "stop":
                return parseLocation(result);

            case "line":
                LOG.debug("Ignoring type \"line\" in search results.");
                return null;

            default:
                // Ignore types so we don't crash if a new type gets introduced. However it may be worth logging so that
                // there is a slightly higher chance we will identify and correctly handle the  missing type.
                LOG.info("Unknown location type \"" + object.getString("type") + "\". Expected either \"stop\" or \"line\"");
                return null;
        }
    }

    /**
     * Parses the JSON response for a "stop" from the /v2/search API.
     *
     * It should contain the following:
     *
     * {
     *   "distance": 0.0,
     *   "suburb": "Belgrave South",
     *   "transport_type": "bus",
     *   "route_type": 2, (not present when querying nearby locations)
     *   "stop_id": 14985,
     *   "location_name": "140 Colby Dr ",
     *   "lat": -37.9302826,
     *   "lon": 145.3589
     * }
     *
     * @see <a href="https://stevage.github.io/PTV-API-doc/#figure10">API docs</a>
     */
    private Location parseLocation(JSONObject object) throws JSONException {
        return new Location(
                LocationType.STATION,
                Integer.toString(object.getInt("stop_id")),
                (int)(object.getDouble("lat") * 1E6),
                (int)(object.getDouble("lon") * 1E6),
                object.getString("suburb"),
                object.getString("location_name"),
                parseProductsFromJson(object.getString("transport_type"))
        );
    }

    // "train", "tram", "bus", "vline" or "nightrider"
    private Set<Product> parseProductsFromJson(String transportType) throws JSONException {
        final Set<Product> products = new HashSet<>(1);
        products.add(parseProductFromTransportType(transportType));
        return products;
    }

    @Nonnull
    private Product parseProductFromTransportType(String transportType) throws JSONException {
        switch(transportType) {
            case "train":
                return Product.SUBURBAN_TRAIN;

            case "tram":
                return Product.TRAM;

            case "bus":
                return Product.BUS;

            case "vline":
                return Product.REGIONAL_TRAIN;

            case "nightrider":
                // Doesn't look like there is a particular type of "Night Bus" available, so just stick with "Bus".
                return Product.BUS;
        }

        throw new JSONException("Invalid transport_type: " + transportType);
    }

    @Override
    public Set<Product> defaultProducts() {
        return EnumSet.of(
                Product.REGIONAL_TRAIN,
                Product.SUBURBAN_TRAIN,
                Product.TRAM,
                Product.BUS
        );
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
    public Point[] getArea() throws IOException {
        return new Point[0];
    }

    /**
     * Adapted from the code provided in the .rtf document by PTV:
     *   https://static.ptv.vic.gov.au/PTV/PTV%20docs/API/1475462320/PTV-Timetable-API-key-and-signature-document.RTF
     * @return A copy of the url, with both "devid=" and "signature=" query parameters appended.
     * @throws SecurityException If the HmacSHA1 algorithm can't be found, or the provided private key is invalid.
     */
    @Nonnull
    public HttpUrl appendSignatureToUrl(@Nonnull final HttpUrl uri) throws SignatureException {
        final HttpUrl uriWithDevId = uri.newBuilder().addQueryParameter("devid", developerId).build();

        final String pathAndQuery = uriWithDevId.encodedPath() + "?" + uriWithDevId.query();
        final byte[] uriBytes = pathAndQuery.getBytes();
        final byte[] keyBytes = privateKey.getBytes();

        final String HMAC_SHA1 = "HmacSHA1";
        final Key signingKey = new SecretKeySpec(keyBytes, HMAC_SHA1);

        Mac mac;
        try {
            mac = Mac.getInstance(HMAC_SHA1);
            mac.init(signingKey);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new SignatureException(e);
        }

        final byte[] signatureBytes = mac.doFinal(uriBytes);

        final StringBuilder signature = new StringBuilder(signatureBytes.length * 2);
        for (byte signatureByte : signatureBytes) {
            int intVal = signatureByte & 0xff;
            if (intVal < 0x10) {
                signature.append("0");
            }
            signature.append(Integer.toHexString(intVal));
        }

        return uriWithDevId.newBuilder().addQueryParameter("signature", signature.toString().toUpperCase()).build();
    }

    public static class SignatureException extends RuntimeException {
        SignatureException(Throwable cause) {
            super("Error while calculating signature of PTV request", cause);
        }
    }

    /**
     * Performs a check of the API end point, including whether the client and server clock
     * are in sync or not.
     *
     * The API documentation suggests to make this call before every other network call you make.
     *
     * @see <a href="https://stevage.github.io/PTV-API-doc/#header13">API Docs</a>
     */
    private void assertHealthCheck() throws RuntimeException, IOException {

        final HttpUrl url = API_BASE_URL.newBuilder()
                .addPathSegment("v2")
                .addPathSegment("healthcheck")
                .addQueryParameter("timestamp", Long.toString(System.currentTimeMillis() / 1000))
                .build();

        HttpUrl urlWithSig = null;
        try {
            urlWithSig = appendSignatureToUrl(url);
        } catch (Exception e) {
            e.printStackTrace();
        }

        final CharSequence response = new HttpClient().get(urlWithSig);

        try {
            final JSONObject jsonResponse = new JSONObject(response.toString());
            final boolean securtityTokenOk = jsonResponse.getBoolean("securityTokenOK");
            final boolean clientClockOk = jsonResponse.getBoolean("clientClockOK");
            final boolean memcacheOK = jsonResponse.getBoolean("memcacheOK");
            final boolean databaseOK = jsonResponse.getBoolean("databaseOK");

            // Don't fall over on clientClock or memcache problems. The clock being wrong does not
            // prevent data coming back. The memcache server not working will cause the queries to
            // be slower. However, securityToken and database will prevent the query from working.
            if (!securtityTokenOk || !databaseOK) {
                throw new RuntimeException(
                        "Health check failed: " +
                                "[securityToken: " + (securtityTokenOk ? "OK" : "Failed") + "] " +
                                "[clientClock: " + (clientClockOk ? "OK" : "Failed") + "] " +
                                "[memcacheOK: " + (memcacheOK ? "OK" : "Failed") + "] " +
                                "[databaseOK: " + (databaseOK ? "OK" : "Failed") + "]");
            }

        } catch (JSONException e) {
            throw new IOException("Error parsing response: " + response);
        }
    }
}
