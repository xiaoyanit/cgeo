package cgeo.geocaching.connector.trackable;

import cgeo.geocaching.AbstractLoggingActivity;
import cgeo.geocaching.Trackable;
import cgeo.geocaching.TrackableLog;
import cgeo.geocaching.connector.UserAction;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import rx.Observable;

import java.util.List;

/**
 * Methods to be implemented by any connector for handling trackables
 *
 */
public interface TrackableConnector {

    /**
     * Return the preference activity for which the connector is attached to.
     * The service could be launched to ask user to configure something.
     *
     * @return the service ID corresponding to the preference activity for the connector
     */
    int getPreferenceActivity();

    boolean canHandleTrackable(@Nullable final String geocode);

    boolean canHandleTrackable(@Nullable final String geocode, @Nullable final TrackableBrand brand);

    /**
     * Return the Title of the service the connector is attached to.
     * Title may be used in messages given to the user, like to say which connector need to
     * be activated for a specific feature.
     *
     * @return the service TITLE corresponding to this connector
     */
    @NonNull
    String getServiceTitle();

    /**
     * Check whether the connector has URLs corresponding the the trackable.
     *
     * @return <tt>true</tt> if the connector handles URLs, <tt>false</tt> otherwise
     */
    boolean hasTrackableUrls();

    /**
     * Return the URL for a trackable. Might throw {@link IllegalStateException} if called
     * on a connector which does not have URLs for trackables. This might be checked using
     * {@link #hasTrackableUrls()}.
     *
     * @param trackable the trackable
     * @return the URL corresponding to this trackable
     */
    @NonNull
    String getUrl(@NonNull final Trackable trackable);

    /**
     * Tell if the trackable has logging capabilities.
     *
     * @return True if trackable is loggable.
     */
    boolean isLoggable();

    /**
     * Return a Trackable corresponding to the Tracable Geocode (Tracking Code) or Guid.
     * Note: Only GC conenctor support guid.
     *
     * @param geocode the trackable Tracking Code
     * @param guid the trackable guid
     * @param id the trackable id
     * @return the Trackable object.
     */
    @Nullable
    Trackable searchTrackable(final String geocode, final String guid, final String id);

    /**
     * Return a Trackable corresponding to the Tracable Geocode.
     *
     * @param geocode the trackable
     * @return the Trackable object.
     */
    @NonNull
    List<Trackable> searchTrackables(final String geocode);

    /**
     * Return a Trackable id from an url.
     *
     * @param url for one trackable
     * @return the Trackable Geocode.
     */
    @Nullable
    String getTrackableCodeFromUrl(final @NonNull String url);

    /**
     * Return available user actions for the trackable.
     *
     * @return the List of available user action.
     */
    @NonNull
    List<UserAction> getUserActions();

    /**
     * Return the Brand object for the Trackable.
     * If Brand could not be defined, return UNKNOWN_BRAND
     *
     * @return the Trackable Brand object.
     */
    @NonNull
    TrackableBrand getBrand();

    /**
     * Return a list of Trackable in user's inventory.
     * In most case, user must be connected to the service.
     *
     * @return the Trackable list.
     */
    @NonNull
    List<Trackable> loadInventory();

    /**
     * Return the Trackable Logging Manager for the Trackable.
     *
     * @param activity currently running
     * @return the Trackable logging manager.
     */
    @Nullable
    AbstractTrackableLoggingManager getTrackableLoggingManager(final AbstractLoggingActivity activity);

    /**
     * Tell if the trackable is loggable via a generic Trackable Connector.
     *
     * @return True if Trackable is loggable via a generic Trackable Connector.
     */
    boolean isGenericLoggable();

    /**
     * Tell if the connector for this trackable is active.
     *
     * @return True if connector is active.
     */
    boolean isActive();

    /**
     * Tell if user is registered to the connector for this trackable.
     *
     * @return True if user is connected to service.
     */
    boolean isRegistered();

    /**
     * Tell if the connector recommend logging a Trackable with Geocode.
     *
     * @return True if connector recommend Geocode.
     */
    boolean recommendLogWithGeocode();

    int getTrackableLoggingManagerLoaderId();

    /**
     * Return list of Trackables in user's inventory converted to TrackableLog object.
     * TrackableLog are used for posting a Trackable Log, they contains necessary
     * informations to post the Trackable Log.
     *
     * @return the TrackableLog corresponding to trackables in user's inventory as Observable.
     */
    @NonNull
    Observable<TrackableLog> trackableLogInventory();
}
