package cgeo.geocaching.apps.navi;

import cgeo.geocaching.R;
import cgeo.geocaching.location.Geopoint;

import org.eclipse.jdt.annotation.NonNull;

import android.app.Activity;
import android.content.Intent;

class OruxMapsApp extends AbstractPointNavigationApp {

    private static final String ORUXMAPS_EXTRA_LONGITUDE = "longitude";
    private static final String ORUXMAPS_EXTRA_LATITUDE = "latitude";
    private static final String INTENT = "com.oruxmaps.VIEW_MAP_ONLINE";

    OruxMapsApp() {
        super(getString(R.string.cache_menu_oruxmaps), INTENT);
    }

    @Override
    public void navigate(final @NonNull Activity activity, final @NonNull Geopoint point) {
        final Intent intent = new Intent(INTENT);
        intent.putExtra(ORUXMAPS_EXTRA_LATITUDE, point.getLatitude());//latitude, wgs84 datum
        intent.putExtra(ORUXMAPS_EXTRA_LONGITUDE, point.getLongitude());//longitude, wgs84 datum

        activity.startActivity(intent);
    }

}
