package cgeo.geocaching.ui;

import cgeo.geocaching.Geocache;
import cgeo.geocaching.ICoordinates;
import cgeo.geocaching.R;
import cgeo.geocaching.Waypoint;
import cgeo.geocaching.connector.ConnectorFactory;
import cgeo.geocaching.location.Units;
import cgeo.geocaching.sensors.Sensors;
import cgeo.geocaching.utils.Formatter;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.jdt.annotation.NonNull;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;

// TODO The suppression of this lint finding is bad. But to fix it, someone needs to rework the layout of the cache
// details also, not just only change the code here.
@SuppressLint("InflateParams")
public final class CacheDetailsCreator {
    private final Activity activity;
    private final ViewGroup parentView;
    private TextView lastValueView;
    private final Resources res;

    public CacheDetailsCreator(final Activity activity, final ViewGroup parentView) {
        this.activity = activity;
        this.res = activity.getResources();
        this.parentView = parentView;
        parentView.removeAllViews();
    }

    /**
     * Create a "name: value" line.
     *
     * @param nameId the resource of the name field
     * @param value the initial value
     * @return a pair made of the whole "name: value" line (to be able to hide it for example) and of the value (to update it)
     */
    public ImmutablePair<RelativeLayout, TextView> add(final int nameId, final CharSequence value) {
        final RelativeLayout layout = (RelativeLayout) activity.getLayoutInflater().inflate(R.layout.cache_information_item, null, false);
        final TextView nameView = ButterKnife.findById(layout, R.id.name);
        nameView.setText(res.getString(nameId));
        lastValueView = ButterKnife.findById(layout, R.id.value);
        lastValueView.setText(value);
        parentView.addView(layout);
        return ImmutablePair.of(layout, lastValueView);
    }

    public TextView getValueView() {
        return lastValueView;
    }

    public RelativeLayout addStars(final int nameId, final float value) {
        return addStars(nameId, value, 5);
    }

    public RelativeLayout addStars(final int nameId, final float value, final int max) {
        final RelativeLayout layout = (RelativeLayout) activity.getLayoutInflater().inflate(R.layout.cache_information_item, null, false);
        final TextView nameView = ButterKnife.findById(layout, R.id.name);
        lastValueView = ButterKnife.findById(layout, R.id.value);

        nameView.setText(activity.getResources().getString(nameId));
        lastValueView.setText(String.format("%.1f", value) + ' ' + activity.getResources().getString(R.string.cache_rating_of) + " " + String.format("%d", max));

        final RatingBar layoutStars = ButterKnife.findById(layout, R.id.stars);
        layoutStars.setNumStars(max);
        layoutStars.setRating(value);
        layoutStars.setVisibility(View.VISIBLE);

        parentView.addView(layout);
        return layout;
    }

    public void addCacheState(final Geocache cache) {
        if (cache.isLogOffline() || cache.isArchived() || cache.isDisabled() || cache.isPremiumMembersOnly() || cache.isFound()) {
            final List<String> states = new ArrayList<>(5);
            String date = getVisitedDate(cache);
            if (cache.isLogOffline()) {
                states.add(res.getString(R.string.cache_status_offline_log) + date);
                // reset the found date, to avoid showing it twice
                date = "";
            }
            if (cache.isFound()) {
                states.add(res.getString(R.string.cache_status_found) + date);
            }
            if (cache.isArchived()) {
                states.add(res.getString(R.string.cache_status_archived));
            }
            if (cache.isDisabled()) {
                states.add(res.getString(R.string.cache_status_disabled));
            }
            if (cache.isPremiumMembersOnly()) {
                states.add(res.getString(R.string.cache_status_premium));
            }
            add(R.string.cache_status, StringUtils.join(states, ", "));
        }
    }

    private static String getVisitedDate(final Geocache cache) {
        final long visited = cache.getVisitedDate();
        return visited != 0 ? " (" + Formatter.formatShortDate(visited) + ")" : "";
    }

    private static Float distanceNonBlocking(final ICoordinates target) {
        if (target.getCoords() == null) {
            return null;
        }
        return Sensors.getInstance().currentGeo().getCoords().distanceTo(target);
    }

    public void addRating(final Geocache cache) {
        if (cache.getRating() > 0) {
            final RelativeLayout itemLayout = addStars(R.string.cache_rating, cache.getRating());
            if (cache.getVotes() > 0) {
                final TextView itemAddition = ButterKnife.findById(itemLayout, R.id.addition);
                itemAddition.setText(" (" + cache.getVotes() + ')');
                itemAddition.setVisibility(View.VISIBLE);
            }
        }
    }

    public void addSize(final Geocache cache) {
        if (cache.showSize()) {
            add(R.string.cache_size, cache.getSize().getL10n());
        }
    }

    public void addDifficulty(final Geocache cache) {
        if (cache.getDifficulty() > 0) {
            addStars(R.string.cache_difficulty, cache.getDifficulty());
        }
    }

    public void addTerrain(final Geocache cache) {
        if (cache.getTerrain() > 0) {
            addStars(R.string.cache_terrain, cache.getTerrain(), ConnectorFactory.getConnector(cache).getMaxTerrain());
        }
    }

    public void addDistance(final Geocache cache, final TextView cacheDistanceView) {
        Float distance = distanceNonBlocking(cache);
        if (distance == null) {
            if (cache.getDistance() != null) {
                distance = cache.getDistance();
            }
        }
        String text = "--";
        if (distance != null) {
            text = Units.getDistanceFromKilometers(distance);
        }
        else if (cacheDistanceView != null) {
            // if there is already a distance in cacheDistance, use it instead of resetting to default.
            // this prevents displaying "--" while waiting for a new position update (See bug #1468)
            text = cacheDistanceView.getText().toString();
        }
        add(R.string.cache_distance, text);
    }

    public void addDistance(final Waypoint wpt, final TextView waypointDistanceView) {
        final Float distance = distanceNonBlocking(wpt);
        String text = "--";
        if (distance != null) {
            text = Units.getDistanceFromKilometers(distance);
        }
        else if (waypointDistanceView != null) {
            // if there is already a distance in waypointDistance, use it instead of resetting to default.
            // this prevents displaying "--" while waiting for a new position update (See bug #1468)
            text = waypointDistanceView.getText().toString();
        }
        add(R.string.cache_distance, text);
    }

    public void addEventDate(@NonNull final Geocache cache) {
        if (!cache.isEventCache()) {
            return;
        }
        addHiddenDate(cache);
    }

    public TextView addHiddenDate(final @NonNull Geocache cache) {
        final String dateString = Formatter.formatHiddenDate(cache);
        if (StringUtils.isEmpty(dateString)) {
            return null;
        }
        final TextView view = add(cache.isEventCache() ? R.string.cache_event : R.string.cache_hidden, dateString).right;
        view.setId(R.id.date);
        return view;
    }
}
