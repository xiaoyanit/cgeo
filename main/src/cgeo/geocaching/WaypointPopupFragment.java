package cgeo.geocaching;

import cgeo.geocaching.apps.navi.NavigationAppFactory;
import cgeo.geocaching.compatibility.Compatibility;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.Units;
import cgeo.geocaching.sensors.GeoData;
import cgeo.geocaching.ui.CacheDetailsCreator;
import cgeo.geocaching.utils.Log;

import org.apache.commons.lang3.StringUtils;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;

public class WaypointPopupFragment extends AbstractDialogFragment {
    @Bind(R.id.actionbar_title) protected TextView actionBarTitle;
    @Bind(R.id.waypoint_details_list) protected LinearLayout waypointDetailsLayout;
    @Bind(R.id.edit) protected Button buttonEdit;
    @Bind(R.id.details_list) protected LinearLayout cacheDetailsLayout;

    private int waypointId = 0;
    private Waypoint waypoint = null;
    private TextView waypointDistance = null;

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View  v = inflater.inflate(R.layout.waypoint_popup, container, false);
        initCustomActionBar(v);
        ButterKnife.bind(this,v);

        return v;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        waypointId = getArguments().getInt(WAYPOINT_ARG);
    }

    @Override
    protected void onUpdateGeoData(final GeoData geo) {
        if (waypoint != null) {
            final Geopoint coordinates = waypoint.getCoords();
            if (coordinates != null) {
                waypointDistance.setText(Units.getDistanceFromKilometers(geo.getCoords().distanceTo(coordinates)));
                waypointDistance.bringToFront();
            }
        }
    }

    @Override
    protected void init() {
        super.init();

        waypoint = DataStore.loadWaypoint(waypointId);

        if (waypoint == null) {
            Log.e("WaypointPopupFragment.init: unable to get waypoint " + waypointId);
            getActivity().finish();
            return;
        }

        try {
            if (StringUtils.isNotBlank(waypoint.getName())) {
                setTitle(waypoint.getName());
            } else {
                setTitle(waypoint.getGeocode());
            }


            actionBarTitle.setCompoundDrawablesWithIntrinsicBounds(Compatibility.getDrawable(getResources(), waypoint.getWaypointType().markerId), null, null, null);

            //getSupportActionBar().setIcon(getResources().getDrawable(waypoint.getWaypointType().markerId));

            details = new CacheDetailsCreator(getActivity(), waypointDetailsLayout);

            //Waypoint geocode
            details.add(R.string.cache_geocode, waypoint.getPrefix() + waypoint.getGeocode().substring(2));
            details.addDistance(waypoint, waypointDistance);
            waypointDistance = details.getValueView();
            details.add(R.string.waypoint_note, waypoint.getNote());

            buttonEdit.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(final View arg0) {
                    EditWaypointActivity.startActivityEditWaypoint(getActivity(), cache, waypoint.getId());
                    getActivity().finish();
                }
            });

            details = new CacheDetailsCreator(getActivity(), cacheDetailsLayout);
            details.add(R.string.cache_name, cache.getName());

            addCacheDetails();

        } catch (final Exception e) {
            Log.e("WaypointPopup.init", e);
        }
    }

    @Override
    public void navigateTo() {
        NavigationAppFactory.startDefaultNavigationApplication(1, getActivity(), waypoint);
    }

    /**
     * Tries to navigate to the {@link Geocache} of this activity.
     */
    @Override
    public void startDefaultNavigation2() {
        if (waypoint == null || waypoint.getCoords() == null) {
            showToast(res.getString(R.string.cache_coordinates_no));
            return;
        }
        NavigationAppFactory.startDefaultNavigationApplication(2, getActivity(), waypoint);
        getActivity().finish();
    }



    @Override
    public void showNavigationMenu() {
        NavigationAppFactory.showNavigationMenu(getActivity(), null, waypoint, null);
    }

    @Override
    protected Geopoint getCoordinates() {
        if (waypoint == null) {
            return null;
        }
        return waypoint.getCoords();
    }

    public static DialogFragment newInstance(final String geocode, final int waypointId) {

        final Bundle args = new Bundle();
        args.putInt(WAYPOINT_ARG, waypointId);
        args.putString(GEOCODE_ARG, geocode);

        final DialogFragment f = new WaypointPopupFragment();
        f.setArguments(args);
        f.setStyle(DialogFragment.STYLE_NO_TITLE,0);

        return f;
    }
}
