package cgeo.geocaching.enumerations;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.Geocache;
import cgeo.geocaching.R;

import org.eclipse.jdt.annotation.NonNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Enum listing all cache types
 */
public enum CacheType {

    TRADITIONAL("traditional", "Traditional Cache", "32bc9333-5e52-4957-b0f6-5a2c8fc7b257", R.string.traditional, R.drawable.type_traditional, "2"),
    MULTI("multi", "Multi-cache", "a5f6d0ad-d2f2-4011-8c14-940a9ebf3c74", R.string.multi, R.drawable.type_multi, "3"),
    MYSTERY("mystery", "Unknown Cache", "40861821-1835-4e11-b666-8d41064d03fe", R.string.mystery, R.drawable.type_mystery, "8"),
    LETTERBOX("letterbox", "Letterbox hybrid", "4bdd8fb2-d7bc-453f-a9c5-968563b15d24", R.string.letterbox, R.drawable.type_letterbox, "5"),
    EVENT("event", "Event Cache", "69eb8534-b718-4b35-ae3c-a856a55b0874", R.string.event, R.drawable.type_event, "6"),
    MEGA_EVENT("mega", "Mega-Event Cache", "69eb8535-b718-4b35-ae3c-a856a55b0874", R.string.mega, R.drawable.type_mega, "453"),
    GIGA_EVENT("giga", "Giga-Event Cache", "51420629-5739-4945-8bdd-ccfd434c0ead", R.string.giga, R.drawable.type_giga, "7005"),
    EARTH("earth", "Earthcache", "c66f5cf3-9523-4549-b8dd-759cd2f18db8", R.string.earth, R.drawable.type_earth, "137"),
    CITO("cito", "Cache in Trash out Event", "57150806-bc1a-42d6-9cf0-538d171a2d22", R.string.cito, R.drawable.type_cito, "13"),
    WEBCAM("webcam", "Webcam Cache", "31d2ae3c-c358-4b5f-8dcd-2185bf472d3d", R.string.webcam, R.drawable.type_webcam, "11"),
    VIRTUAL("virtual", "Virtual Cache", "294d4360-ac86-4c83-84dd-8113ef678d7e", R.string.virtual, R.drawable.type_virtual, "4"),
    WHERIGO("wherigo", "Wherigo Cache", "0544fa55-772d-4e5c-96a9-36a51ebcf5c9", R.string.wherigo, R.drawable.type_wherigo, "1858"),
    LOSTANDFOUND("lostfound", "Lost and Found Event Cache", "3ea6533d-bb52-42fe-b2d2-79a3424d4728", R.string.lostfound, R.drawable.type_event, "3653"), // icon missing
    PROJECT_APE("ape", "Project Ape Cache", "2555690d-b2bc-4b55-b5ac-0cb704c0b768", R.string.ape, R.drawable.type_ape, "2"),
    GCHQ("gchq", "Groundspeak HQ", "416f2494-dc17-4b6a-9bab-1a29dd292d8c", R.string.gchq, R.drawable.type_hq, "3773"),
    GPS_EXHIBIT("gps", "GPS Adventures Exhibit", "72e69af2-7986-4990-afd9-bc16cbbb4ce3", R.string.gps, R.drawable.type_event, "1304"), // icon missing
    BLOCK_PARTY("block", "Groundspeak Block Party", "bc2f3df2-1aab-4601-b2ff-b5091f6c02e3", R.string.block, R.drawable.type_event, "4738"), // icon missing
    UNKNOWN("unknown", "unknown", "", R.string.unknown, R.drawable.type_unknown, ""),
    /** No real cache type -> filter */
    ALL("all", "display all caches", "9a79e6ce-3344-409c-bbe9-496530baf758", R.string.all_types, R.drawable.type_unknown, "");

    /**
     * id field is used when for storing caches in the database.
     */
    public final String id;
    /**
     * human readable name of the cache type<br>
     * used in web parsing as well as for gpx import/export.
     */
    public final String pattern;
    public final String guid;
    private final int stringId;
    public final int markerId;
    @NonNull public final String wptTypeId;

    CacheType(final String id, final String pattern, final String guid, final int stringId, final int markerId, @NonNull final String wptTypeId) {
        this.id = id;
        this.pattern = pattern;
        this.guid = guid;
        this.stringId = stringId;
        this.markerId = markerId;
        this.wptTypeId = wptTypeId;
    }

    @NonNull
    private final static Map<String, CacheType> FIND_BY_ID = new HashMap<>();
    @NonNull
    private final static Map<String, CacheType> FIND_BY_PATTERN = new HashMap<>();
    @NonNull
    private final static Map<String, CacheType> FIND_BY_GUID = new HashMap<>();

    static {
        for (final CacheType ct : values()) {
            FIND_BY_ID.put(ct.id, ct);
            FIND_BY_PATTERN.put(ct.pattern.toLowerCase(Locale.US), ct);
            FIND_BY_GUID.put(ct.guid, ct);
        }
        // Add old mystery type for GPX file compatibility.
        FIND_BY_PATTERN.put("Mystery Cache".toLowerCase(Locale.US), MYSTERY);
        // This pattern briefly appeared on gc.com in 2014-08.
        FIND_BY_PATTERN.put("Traditional Geocache".toLowerCase(Locale.US), TRADITIONAL);
        // map lab caches to the virtual type for the time being
        FIND_BY_PATTERN.put("Lab Cache".toLowerCase(Locale.US), VIRTUAL);
    }

    @NonNull
    public static CacheType getById(final String id) {
        final CacheType result = (id != null) ? FIND_BY_ID.get(id.toLowerCase(Locale.US).trim()) : null;
        if (result == null) {
            return UNKNOWN;
        }
        return result;
    }

    @NonNull
    public static CacheType getByPattern(final String pattern) {
        final CacheType result = (pattern != null) ? FIND_BY_PATTERN.get(pattern.toLowerCase(Locale.US).trim()) : null;
        if (result == null) {
            return UNKNOWN;
        }
        return result;
    }

    @NonNull
    public static CacheType getByGuid(final String id) {
        final CacheType result = (id != null) ? FIND_BY_GUID.get(id) : null;
        if (result == null) {
            return UNKNOWN;
        }
        return result;
    }

    @NonNull
    public final String getL10n() {
        return CgeoApplication.getInstance().getBaseContext().getResources().getString(stringId);
    }

    public boolean isEvent() {
        return EVENT == this || MEGA_EVENT == this || CITO == this || GIGA_EVENT == this || LOSTANDFOUND == this || BLOCK_PARTY == this || GPS_EXHIBIT == this;
    }

    @Override
    public String toString() {
        return getL10n();
    }

    /**
     * Whether this type contains the given cache.
     *
     * @return true if this is the ALL type or if this type equals the type of the cache.
     */
    public boolean contains(final Geocache cache) {
        if (cache == null) {
            return false;
        }
        if (this == ALL) {
            return true;
        }
        return cache.getType() == this;
    }

    public boolean applyDistanceRule() {
        return TRADITIONAL == this || PROJECT_APE == this || GCHQ == this;
    }

    public boolean isVirtual() {
        return VIRTUAL == this || WEBCAM == this || EARTH == this;
    }
}
