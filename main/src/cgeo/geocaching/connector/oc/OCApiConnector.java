package cgeo.geocaching.connector.oc;

import cgeo.geocaching.Geocache;
import cgeo.geocaching.SearchResult;
import cgeo.geocaching.connector.capability.ISearchByGeocode;
import cgeo.geocaching.network.Parameters;
import cgeo.geocaching.utils.CancellableHandler;
import cgeo.geocaching.utils.CryptUtils;
import cgeo.geocaching.utils.RxUtils;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import rx.Observable;
import rx.functions.Func0;

public class OCApiConnector extends OCConnector implements ISearchByGeocode {

    // Levels of Okapi we support
    // oldapi is around rev 500
    // current is from rev 798 onwards
    public enum ApiSupport {
        oldapi,
        current
    }

    // Levels of OAuth-Authentication we support
    public enum OAuthLevel {
        Level1,
        Level3
    }

    private final String cK;
    private final ApiSupport apiSupport;
    private final String licenseString;

    public OCApiConnector(final String name, final String host, final String prefix, final String cK, final String licenseString, final ApiSupport apiSupport) {
        super(name, host, prefix);
        this.cK = cK;
        this.apiSupport = apiSupport;
        this.licenseString = licenseString;
    }

    public void addAuthentication(final Parameters params) {
        final String rotCK = CryptUtils.rot13(cK);
        // check that developers are not using the Ant defined properties without any values
        if (StringUtils.startsWith(rotCK, "${")) {
            throw new IllegalStateException("invalid OKAPI OAuth token " + rotCK);
        }
        params.put(CryptUtils.rot13("pbafhzre_xrl"), rotCK);
    }

    @Override
    @NonNull
    public String getLicenseText(final @NonNull Geocache cache) {
        // NOT TO BE TRANSLATED
        return "© " + cache.getOwnerDisplayName() + ", <a href=\"" + getCacheUrl(cache) + "\">" + getName() + "</a>, " + licenseString;
    }

    @Override
    public SearchResult searchByGeocode(final @Nullable String geocode, final @Nullable String guid, final CancellableHandler handler) {
        final Geocache cache = OkapiClient.getCache(geocode);
        if (cache == null) {
            return null;
        }
        return new SearchResult(cache);
    }

    @Override
    public boolean isActive() {
        // currently always active, but only for details download
        return true;
    }

    @SuppressWarnings("static-method")
    public OAuthLevel getSupportedAuthLevel() {
        return OAuthLevel.Level1;
    }

    public String getCK() {
        return CryptUtils.rot13(cK);
    }

    @SuppressWarnings("static-method")
    public String getCS() {
        return StringUtils.EMPTY;
    }

    public ApiSupport getApiSupport() {
        return apiSupport;
    }

    @SuppressWarnings("static-method")
    public int getTokenPublicPrefKeyId() {
        return 0;
    }

    @SuppressWarnings("static-method")
    public int getTokenSecretPrefKeyId() {
        return 0;
    }

    /**
     * Checks if a search based on a user name targets the current user
     *
     * @param username
     *            Name of the user the query is searching after
     * @return True - search target and current is same, False - current user not known or not the same as username
     */
    @SuppressWarnings("static-method")
    public boolean isSearchForMyCaches(final String username) {
        return false;
    }

    @Override
    @Nullable
    public String getGeocodeFromUrl(@NonNull final String url) {
        final String shortHost = StringUtils.remove(getHost(), "www.");

        // host.tld/viewcache.php?cacheid
        final String id = StringUtils.trim(StringUtils.substringAfter(url, shortHost + "/viewcache.php?cacheid="));
        if (StringUtils.isNotBlank(id)) {

            final String geocode = Observable.defer(new Func0<Observable<String>>() {
                @Override
                public Observable<String> call() {
                    return Observable.just(OkapiClient.getGeocodeByUrl(OCApiConnector.this, url));
                }
            }).subscribeOn(RxUtils.networkScheduler).toBlocking().first();

            if (geocode != null && canHandle(geocode)) {
                return geocode;
            }
        }

        return super.getGeocodeFromUrl(url);
    }

}
