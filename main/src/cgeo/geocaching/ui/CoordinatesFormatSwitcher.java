package cgeo.geocaching.ui;

import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.GeopointFormatter;

import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

/**
 * view click listener to automatically switch different coordinate formats
 *
 */
public class CoordinatesFormatSwitcher implements OnClickListener {

    private static final GeopointFormatter.Format[] availableFormats = {
            GeopointFormatter.Format.LAT_LON_DECMINUTE,
            GeopointFormatter.Format.LAT_LON_DECSECOND,
            GeopointFormatter.Format.LAT_LON_DECDEGREE
    };

    private int position = 0;

    private final Geopoint coordinates;

    public CoordinatesFormatSwitcher(final Geopoint coordinates) {
        this.coordinates = coordinates;
    }

    @Override
    public void onClick(final View view) {
        assert view instanceof TextView;
        position = (position + 1) % availableFormats.length;
        final TextView textView = (TextView) view;
        // rotate coordinate formats on click
        textView.setText(coordinates.format(availableFormats[position]));
    }

}