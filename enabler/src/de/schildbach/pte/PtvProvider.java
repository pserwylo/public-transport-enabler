package de.schildbach.pte;

import de.schildbach.pte.dto.*;
import okhttp3.HttpUrl;

import javax.annotation.Nonnull;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
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

    /**
     * Adapted from the code provided in the .rtf document by PTV:
     *   https://static.ptv.vic.gov.au/PTV/PTV%20docs/API/1475462320/PTV-Timetable-API-key-and-signature-document.RTF
     * @param developerId See the linked RTF document above about obtaining a developer ID.
     * @return A copy of the url, with both "devid=" and "signature=" query parameters appended.
     * @throws SecurityException If the HmacSHA1 algorithm can't be found, or the provided private key is invalid.
     */
    @Nonnull
    public static HttpUrl appendSignatureToUrl(HttpUrl url, int developerId, String privateKey) throws SecurityException {
        String HMAC_SHA1_ALGORITHM = "HmacSHA1";
        final HttpUrl urlWithDevId = url.newBuilder()
                .addQueryParameter("devid", Integer.toString(developerId))
                .build();

        byte[] keyBytes = privateKey.getBytes();
        byte[] uriBytes = urlWithDevId.toString().getBytes();
        Key signingKey = new SecretKeySpec(keyBytes, HMAC_SHA1_ALGORITHM);

        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signingKey);

            byte[] signatureBytes = mac.doFinal(uriBytes);
            StringBuilder signature = new StringBuilder(signatureBytes.length * 2);
            for (byte signatureByte : signatureBytes) {
                int intVal = signatureByte & 0xff;
                if (intVal < 0x10) {
                    signature.append("0");
                }
                signature.append(Integer.toHexString(intVal));
            }

            return urlWithDevId.newBuilder()
                    .addQueryParameter("signature", signature.toString())
                    .build();

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new SecurityException("Error attaching signature to PTV request", e);
        }
    }
}
