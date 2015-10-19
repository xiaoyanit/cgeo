package cgeo.geocaching.apps.navi;

import cgeo.geocaching.Geocache;
import cgeo.geocaching.Intents;
import cgeo.geocaching.R;
import cgeo.geocaching.Waypoint;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.utils.ProcessUtils;

import org.eclipse.jdt.annotation.NonNull;

import android.app.Activity;
import android.content.Intent;

/**
 * For use with any Android Wear geocaching apps which can handle the intent action below.
 */
class AndroidWearApp extends AbstractPointNavigationApp {
    private static final String INTENT_ACTION = "cgeo.geocaching.wear.NAVIGATE_TO";
    private static final String INTENT_PACKAGE = "com.javadog.cgeowear";

    public AndroidWearApp() {
        super(getString(R.string.cache_menu_android_wear), INTENT_ACTION, null);
    }

    @Override
    public boolean isInstalled() {
        return ProcessUtils.isIntentAvailable(INTENT_ACTION);
    }

    @Override
    public void navigate(final @NonNull Activity activity, final @NonNull Geopoint coords) {
        navigate(activity, null, null, coords);
    }

    @Override
    public void navigate(final @NonNull Activity activity, final @NonNull Geocache cache) {
        navigate(activity, cache.getName(), cache.getGeocode(), cache.getCoords());
    }

    @Override
    public void navigate(final @NonNull Activity activity, final @NonNull Waypoint waypoint) {
        navigate(activity, waypoint.getName(), waypoint.getGeocode(), waypoint.getCoords());
    }

    private static void navigate(final Activity activity, final String destName,
                                 final String destCode, final Geopoint coords) {
        final Intent launchIntent = new Intent(INTENT_ACTION);
        launchIntent.setPackage(INTENT_PACKAGE);
        launchIntent.putExtra(Intents.EXTRA_NAME, destName)
                .putExtra(Intents.EXTRA_GEOCODE, destCode)
                .putExtra(Intents.EXTRA_LATITUDE, coords.getLatitude())
                .putExtra(Intents.EXTRA_LONGITUDE, coords.getLongitude());
        activity.startService(launchIntent);
    }
}
