package cgeo.geocaching.utils;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.Geocache;
import cgeo.geocaching.R;
import cgeo.geocaching.Waypoint;
import cgeo.geocaching.enumerations.CacheSize;
import cgeo.geocaching.enumerations.WaypointType;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import android.content.Context;
import android.text.format.DateUtils;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class Formatter {

    /** Text separator used for formatting texts */
    public static final String SEPARATOR = " · ";

    private static final Context context = CgeoApplication.getInstance().getBaseContext();

    private Formatter() {
        // Utility class
    }

    /**
     * Generate a time string according to system-wide settings (locale, 12/24 hour)
     * such as "13:24".
     *
     * @param date
     *            milliseconds since the epoch
     * @return the formatted string
     */
    @NonNull
    public static String formatTime(final long date) {
        return DateUtils.formatDateTime(context, date, DateUtils.FORMAT_SHOW_TIME);
    }

    /**
     * Generate a date string according to system-wide settings (locale, date format)
     * such as "20 December" or "20 December 2010". The year will only be included when necessary.
     *
     * @param date
     *            milliseconds since the epoch
     * @return the formatted string
     */
    @NonNull
    public static String formatDate(final long date) {
        return DateUtils.formatDateTime(context, date, DateUtils.FORMAT_SHOW_DATE);
    }

    /**
     * Generate a date string according to system-wide settings (locale, date format)
     * such as "20 December 2010". The year will always be included, making it suitable
     * to generate long-lived log entries.
     *
     * @param date
     *            milliseconds since the epoch
     * @return the formatted string
     */
    @NonNull
    public static String formatFullDate(final long date) {
        return DateUtils.formatDateTime(context, date, DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_SHOW_YEAR);
    }

    /**
     * Generate a numeric date string according to system-wide settings (locale, date format)
     * such as "10/20/2010".
     *
     * @param date
     *            milliseconds since the epoch
     * @return the formatted string
     */
    @NonNull
    public static String formatShortDate(final long date) {
        final DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(context);
        return dateFormat.format(date);
    }

    private static String formatShortDateIncludingWeekday(final long time) {
        return DateUtils.formatDateTime(CgeoApplication.getInstance().getBaseContext(), time, DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY) + ", " + formatShortDate(time);
    }

    /**
     * Generate a numeric date string according to system-wide settings (locale, date format)
     * such as "10/20/2010". Today and yesterday will be presented as strings "today" and "yesterday".
     *
     * @param date
     *            milliseconds since the epoch
     * @return the formatted string
     */
    @NonNull
    public static String formatShortDateVerbally(final long date) {
        final int diff = CalendarUtils.daysSince(date);
        switch (diff) {
            case 0:
                return CgeoApplication.getInstance().getString(R.string.log_today);
            case 1:
                return CgeoApplication.getInstance().getString(R.string.log_yesterday);
            default:
                return formatShortDate(date);
        }
    }

    /**
     * Generate a numeric date and time string according to system-wide settings (locale,
     * date format) such as "7 sept. at 12:35".
     *
     * @param date
     *            milliseconds since the epoch
     * @return the formatted string
     */
    @NonNull
    public static String formatShortDateTime(final long date) {
        return DateUtils.formatDateTime(context, date, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL);
    }

    /**
     * Generate a numeric date and time string according to system-wide settings (locale,
     * date format) such as "7 september at 12:35".
     *
     * @param date
     *            milliseconds since the epoch
     * @return the formatted string
     */
    @NonNull
    public static String formatDateTime(final long date) {
        return DateUtils.formatDateTime(context, date, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);
    }

    @NonNull
    public static String formatCacheInfoLong(final Geocache cache) {
        final List<String> infos = new ArrayList<>();
        if (StringUtils.isNotBlank(cache.getGeocode())) {
            infos.add(cache.getGeocode());
        }

        addShortInfos(cache, infos);

        if (cache.isPremiumMembersOnly()) {
            infos.add(CgeoApplication.getInstance().getString(R.string.cache_premium));
        }
        if (cache.isOffline()) {
            infos.add(CgeoApplication.getInstance().getString(R.string.cache_offline));
        }
        return StringUtils.join(infos, SEPARATOR);
    }

    @NonNull
    public static String formatCacheInfoShort(final Geocache cache) {
        final List<String> infos = new ArrayList<>();
        addShortInfos(cache, infos);
        return StringUtils.join(infos, SEPARATOR);
    }

    private static void addShortInfos(final Geocache cache, final List<String> infos) {
        if (cache.hasDifficulty()) {
            infos.add("D " + formatDT(cache.getDifficulty()));
        }
        if (cache.hasTerrain()) {
            infos.add("T " + formatDT(cache.getTerrain()));
        }

        // don't show "not chosen" for events and virtuals, that should be the normal case
        if (cache.getSize() != CacheSize.UNKNOWN && cache.showSize()) {
            infos.add(cache.getSize().getL10n());
        } else if (cache.isEventCache()) {
            final Date hiddenDate = cache.getHiddenDate();
            if (hiddenDate != null) {
                infos.add(formatShortDateIncludingWeekday(hiddenDate.getTime()));
            }
        }
    }

    private static String formatDT(final float value) {
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    @NonNull
    public static String formatCacheInfoHistory(final Geocache cache) {
        final List<String> infos = new ArrayList<>(3);
        infos.add(StringUtils.upperCase(cache.getGeocode()));
        infos.add(formatDate(cache.getVisitedDate()));
        infos.add(formatTime(cache.getVisitedDate()));
        return StringUtils.join(infos, SEPARATOR);
    }

    @NonNull
    public static String formatWaypointInfo(final Waypoint waypoint) {
        final List<String> infos = new ArrayList<>(3);
        final WaypointType waypointType = waypoint.getWaypointType();
        if (waypointType != WaypointType.OWN && waypointType != null) {
            infos.add(waypointType.getL10n());
        }
        if (Waypoint.PREFIX_OWN.equalsIgnoreCase(waypoint.getPrefix())) {
            infos.add(CgeoApplication.getInstance().getString(R.string.waypoint_custom));
        } else {
            if (StringUtils.isNotBlank(waypoint.getPrefix())) {
                infos.add(waypoint.getPrefix());
            }
            if (StringUtils.isNotBlank(waypoint.getLookup())) {
                infos.add(waypoint.getLookup());
            }
        }
        return StringUtils.join(infos, SEPARATOR);
    }

    @NonNull
    public static String formatDaysAgo(final long date) {
        final int days = CalendarUtils.daysSince(date);
        switch (days) {
            case 0:
                return CgeoApplication.getInstance().getString(R.string.log_today);
            case 1:
                return CgeoApplication.getInstance().getString(R.string.log_yesterday);
            default:
                return CgeoApplication.getInstance().getResources().getQuantityString(R.plurals.days_ago, days, days);
        }
    }

    /**
     * Formatting of the hidden date of a cache
     *
     * @return {@code null} or hidden date of the cache (or event date of the cache) in human readable format
     */
    @Nullable
    public static String formatHiddenDate(final Geocache cache) {
        final Date hiddenDate = cache.getHiddenDate();
        if (hiddenDate == null) {
            return null;
        }
        final long time = hiddenDate.getTime();
        if (time <= 0) {
            return null;
        }
        String dateString = formatFullDate(time);
        if (cache.isEventCache()) {
            dateString = DateUtils.formatDateTime(CgeoApplication.getInstance().getBaseContext(), time, DateUtils.FORMAT_SHOW_WEEKDAY) + ", " + dateString;
        }
        return dateString;
    }

    @NonNull
    public static String formatMapSubtitle(final Geocache cache) {
        return "D " + formatDT(cache.getDifficulty()) + SEPARATOR + "T " + formatDT(cache.getTerrain()) + SEPARATOR + cache.getGeocode();
    }

}
