package cgeo.geocaching.apps.navi;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.R;
import cgeo.geocaching.activity.ActivityMixin;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.utils.ProcessUtils;

import org.eclipse.jdt.annotation.NonNull;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

class StreetviewApp extends AbstractPointNavigationApp {

    private static final String PACKAGE_NAME_STREET_VIEW = "com.google.android.street";
    private static final boolean INSTALLED = ProcessUtils.isInstalled(PACKAGE_NAME_STREET_VIEW);

    StreetviewApp() {
        super(getString(R.string.cache_menu_streetview), null);
    }

    @Override
    public boolean isInstalled() {
        return INSTALLED;
    }

    @Override
    public void navigate(final @NonNull Activity activity, final @NonNull Geopoint point) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("google.streetview:cbll=" + point.getLatitude() + "," + point.getLongitude())));
        } catch (final ActivityNotFoundException ignored) {
            ActivityMixin.showToast(activity, CgeoApplication.getInstance().getString(R.string.err_application_no));
        }
    }
}