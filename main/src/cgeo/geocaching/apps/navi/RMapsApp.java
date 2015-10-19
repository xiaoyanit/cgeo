package cgeo.geocaching.apps.navi;

import cgeo.geocaching.Geocache;
import cgeo.geocaching.R;
import cgeo.geocaching.Waypoint;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.GeopointFormatter.Format;

import org.eclipse.jdt.annotation.NonNull;

import android.app.Activity;
import android.content.Intent;

import java.util.ArrayList;

class RMapsApp extends AbstractPointNavigationApp {

    private static final String INTENT = "com.robert.maps.action.SHOW_POINTS";

    RMapsApp() {
        super(getString(R.string.cache_menu_rmaps), INTENT);
    }

    @Override
    public void navigate(final @NonNull Activity activity, final @NonNull Waypoint waypoint) {
        navigate(activity, waypoint.getCoords(), waypoint.getLookup(), waypoint.getName());
    }

    private static void navigate(final Activity activity, final Geopoint coords, final String code, final String name) {
        final ArrayList<String> locations = new ArrayList<>();
        locations.add(coords.format(Format.LAT_LON_DECDEGREE_COMMA) + ";" + code + ";" + name);
        final Intent intent = new Intent(INTENT);
        intent.putStringArrayListExtra("locations", locations);
        activity.startActivity(intent);
    }

    @Override
    public void navigate(final @NonNull Activity activity, final @NonNull Geocache cache) {
        navigate(activity, cache.getCoords(), cache.getGeocode(), cache.getName());
    }

    @Override
    public void navigate(final @NonNull Activity activity, final @NonNull Geopoint coords) {
        navigate(activity, coords, "", "");
    }
}
