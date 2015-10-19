package cgeo.geocaching;

import butterknife.ButterKnife;
import butterknife.Bind;

import cgeo.geocaching.activity.AbstractActivity;
import cgeo.geocaching.activity.AbstractViewPagerActivity;
import cgeo.geocaching.connector.ConnectorFactory;
import cgeo.geocaching.connector.trackable.TrackableBrand;
import cgeo.geocaching.connector.trackable.TrackableConnector;
import cgeo.geocaching.connector.trackable.TravelBugConnector;
import cgeo.geocaching.enumerations.LogType;
import cgeo.geocaching.location.Units;
import cgeo.geocaching.network.AndroidBeam;
import cgeo.geocaching.network.HtmlImage;
import cgeo.geocaching.sensors.GeoData;
import cgeo.geocaching.sensors.GeoDirHandler;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.ui.AbstractCachingPageViewCreator;
import cgeo.geocaching.ui.AnchorAwareLinkMovementMethod;
import cgeo.geocaching.ui.CacheDetailsCreator;
import cgeo.geocaching.ui.ImagesList;
import cgeo.geocaching.ui.UserActionsClickListener;
import cgeo.geocaching.ui.UserNameClickListener;
import cgeo.geocaching.ui.logs.TrackableLogsViewCreator;
import cgeo.geocaching.utils.Formatter;
import cgeo.geocaching.utils.HtmlUtils;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.RxUtils;
import cgeo.geocaching.utils.UnknownTagsHandler;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.Nullable;

import rx.Observable;
import rx.Subscription;
import rx.android.app.AppObservable;
import rx.android.view.OnClickEvent;
import rx.android.view.ViewObservable;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;
import rx.subscriptions.Subscriptions;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.view.ActionMode;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrackableActivity extends AbstractViewPagerActivity<TrackableActivity.Page> implements AndroidBeam.ActivitySharingInterface {

    private CompositeSubscription createSubscriptions;

    public enum Page {
        DETAILS(R.string.detail),
        LOGS(R.string.cache_logs),
        IMAGES(R.string.cache_images);

        private final int resId;

        Page(final int resId) {
            this.resId = resId;
        }
    }

    private Trackable trackable = null;
    private String geocode = null;
    private String name = null;
    private String guid = null;
    private String id = null;
    private String geocache = null;
    private String trackingCode = null;
    private TrackableBrand brand = null;
    private LayoutInflater inflater = null;
    private ProgressDialog waitDialog = null;
    private CharSequence clickedItemText = null;
    private ImagesList imagesList = null;
    private Subscription geoDataSubscription = Subscriptions.empty();
    private static final GeoDirHandler locationUpdater = new GeoDirHandler() {
        @Override
        public void updateGeoData(final GeoData geoData) {
            // Do not do anything, as we just want to maintain the GPS on
        }
    };

    /**
     * Action mode of the current contextual action bar (e.g. for copy and share actions).
     */
    private ActionMode currentActionMode;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, R.layout.viewpager_activity);
        createSubscriptions = new CompositeSubscription();

        // set title in code, as the activity needs a hard coded title due to the intent filters
        setTitle(res.getString(R.string.trackable));

        // get parameters
        final Bundle extras = getIntent().getExtras();

        final Uri uri = AndroidBeam.getUri(getIntent());
        if (extras != null) {
            // try to get data from extras
            geocode = extras.getString(Intents.EXTRA_GEOCODE);
            name = extras.getString(Intents.EXTRA_NAME);
            guid = extras.getString(Intents.EXTRA_GUID);
            id = extras.getString(Intents.EXTRA_ID);
            geocache = extras.getString(Intents.EXTRA_GEOCACHE);
            brand = TrackableBrand.getById(extras.getInt(Intents.EXTRA_BRAND));
            trackingCode = extras.getString(Intents.EXTRA_TRACKING_CODE);

        }

        // try to get data from URI
        if (geocode == null && guid == null && id == null && uri != null) {
            geocode = ConnectorFactory.getTrackableFromURL(uri.toString());

            final String uriHost = uri.getHost().toLowerCase(Locale.US);
            if (uriHost.endsWith("geocaching.com")) {
                geocode = uri.getQueryParameter("tracker");
                guid = uri.getQueryParameter("guid");
                id = uri.getQueryParameter("id");

                if (StringUtils.isNotBlank(geocode)) {
                    geocode = geocode.toUpperCase(Locale.US);
                    guid = null;
                    id = null;
                } else if (StringUtils.isNotBlank(guid)) {
                    geocode = null;
                    guid = guid.toLowerCase(Locale.US);
                    id = null;
                } else if (StringUtils.isNotBlank(id)) {
                    geocode = null;
                    guid = null;
                    id = id.toLowerCase(Locale.US);
                } else {
                    showToast(res.getString(R.string.err_tb_details_open));
                    finish();
                    return;
                }
            }
        }

        // no given data
        if (geocode == null && guid == null && id == null) {
            showToast(res.getString(R.string.err_tb_display));
            finish();
            return;
        }

        final String message;
        if (StringUtils.isNotBlank(name)) {
            message = Html.fromHtml(name).toString();
        } else if (StringUtils.isNotBlank(geocode)) {
            message = geocode;
        } else {
            message = res.getString(R.string.trackable);
        }

        // If we have a newer Android device setup Android Beam for easy cache sharing
        AndroidBeam.enable(this, this);

        createViewPager(0, new OnPageSelectedListener() {
            @Override
            public void onPageSelected(final int position) {
                lazyLoadTrackableImages();
            }
        });
        refreshTrackable(message);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!Settings.useLowPowerMode()) {
            geoDataSubscription = locationUpdater.start(GeoDirHandler.UPDATE_GEODATA);
        }
    }

    @Override
    public void onPause() {
        geoDataSubscription.unsubscribe();
        super.onPause();
    }

    private void refreshTrackable(final String message) {
        waitDialog = ProgressDialog.show(this, message, res.getString(R.string.trackable_details_loading), true, true);
        createSubscriptions.add(AppObservable.bindActivity(this, loadTrackable(geocode, guid, id, brand)).singleOrDefault(null).subscribe(new Action1<Trackable>() {
            @Override
            public void call(final Trackable trackable) {
                if (trackable != null && trackingCode != null) {
                    trackable.setTrackingcode(trackingCode);
                }
                TrackableActivity.this.trackable = trackable;
                displayTrackable();
                // reset imagelist
                imagesList = null;
                lazyLoadTrackableImages();
            }
        }));
    }

    @Nullable
    @Override
    public String getAndroidBeamUri() {
        return trackable != null ? trackable.getUrl() : null;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.trackable_activity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_log_touch:
                startActivityForResult(LogTrackableActivity.getIntent(this, trackable, geocache), LogTrackableActivity.LOG_TRACKABLE);
                return true;
            case R.id.menu_browser_trackable:
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(trackable.getUrl())));
                return true;
            case R.id.menu_refresh_trackable:
                refreshTrackable(StringUtils.defaultIfBlank(trackable.getName(), trackable.getGeocode()));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        if (trackable != null) {
            menu.findItem(R.id.menu_log_touch).setVisible(StringUtils.isNotBlank(geocode) && trackable.isLoggable());
            menu.findItem(R.id.menu_browser_trackable).setVisible(trackable.hasUrl());
            menu.findItem(R.id.menu_refresh_trackable).setVisible(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private static Observable<Trackable> loadTrackable(final String geocode, final String guid, final String id, final TrackableBrand brand) {
        if (StringUtils.isEmpty(geocode)) {
            // Only solution is GC search by uid
            return RxUtils.deferredNullable(new Func0<Trackable>() {
                @Override
                public Trackable call() {
                    return TravelBugConnector.getInstance().searchTrackable(geocode, guid, id);
                }
            }).subscribeOn(RxUtils.networkScheduler);
        }

        // We query all the connectors that can handle the trackable in parallel as well as the local storage.
        // We return the first positive result coming from a connector, or, if none, the result of loading from
        // the local storage.

        final Observable<Trackable> fromNetwork =
                Observable.from(ConnectorFactory.getTrackableConnectors()).filter(new Func1<TrackableConnector, Boolean>() {
                    @Override
                    public Boolean call(final TrackableConnector trackableConnector) {
                        return trackableConnector.canHandleTrackable(geocode, brand);
                    }
                }).flatMap(new Func1<TrackableConnector, Observable<Trackable>>() {
                    @Override
                    public Observable<Trackable> call(final TrackableConnector trackableConnector) {
                        return RxUtils.deferredNullable(new Func0<Trackable>() {
                            @Override
                            public Trackable call() {
                                return trackableConnector.searchTrackable(geocode, guid, id);
                            }
                        }).subscribeOn(RxUtils.networkScheduler);
                    }
                });

        final Observable<Trackable> fromLocalStorage = RxUtils.deferredNullable(new Func0<Trackable>() {
            @Override
            public Trackable call() {
                return DataStore.loadTrackable(geocode);
            }
        }).subscribeOn(Schedulers.io());

        return fromNetwork.concatWith(fromLocalStorage).take(1);
    }

    public void displayTrackable() {
        if (trackable == null) {
            if (waitDialog != null) {
                waitDialog.dismiss();
            }

            if (StringUtils.isNotBlank(geocode)) {
                showToast(res.getString(R.string.err_tb_find) + " " + geocode + ".");
            } else {
                showToast(res.getString(R.string.err_tb_find_that));
            }

            finish();
            return;
        }

        try {
            inflater = getLayoutInflater();
            geocode = trackable.getGeocode();

            if (StringUtils.isNotBlank(trackable.getName())) {
                setTitle(Html.fromHtml(trackable.getName()).toString());
            } else {
                setTitle(trackable.getName());
            }

            invalidateOptionsMenuCompatible();
            reinitializeViewPager();

        } catch (final Exception e) {
            Log.e("TrackableActivity.loadTrackableHandler: ", e);
        }

        if (waitDialog != null) {
            waitDialog.dismiss();
        }

    }

    private void setupIcon(final ActionBar actionBar, final String url) {
        final HtmlImage imgGetter = new HtmlImage(HtmlImage.SHARED, false, 0, false);
        AppObservable.bindActivity(this, imgGetter.fetchDrawable(url)).subscribe(new Action1<BitmapDrawable>() {
            @Override
            public void call(final BitmapDrawable image) {
                if (actionBar != null) {
                    final int height = actionBar.getHeight();
                    image.setBounds(0, 0, height, height);
                    actionBar.setIcon(image);
                }
            }
        });
    }

    private static void setupIcon(final ActionBar actionBar, final int resId) {
        if (actionBar != null) {
            actionBar.setIcon(resId);
        }
    }

    public static void startActivity(final AbstractActivity fromContext,
            final String guid, final String geocode, final String name, final String geocache, final int brandId) {
        final Intent trackableIntent = new Intent(fromContext, TrackableActivity.class);
        trackableIntent.putExtra(Intents.EXTRA_GUID, guid);
        trackableIntent.putExtra(Intents.EXTRA_GEOCODE, geocode);
        trackableIntent.putExtra(Intents.EXTRA_NAME, name);
        trackableIntent.putExtra(Intents.EXTRA_GEOCACHE, geocache);
        trackableIntent.putExtra(Intents.EXTRA_BRAND, brandId);
        fromContext.startActivity(trackableIntent);
    }

    @Override
    protected PageViewCreator createViewCreator(final Page page) {
        switch (page) {
            case DETAILS:
                return new DetailsViewCreator();
            case LOGS:
                return new TrackableLogsViewCreator(this);
            case IMAGES:
                return new ImagesViewCreator();
        }
        throw new IllegalStateException(); // cannot happen as long as switch case is enum complete
    }

    private class ImagesViewCreator extends AbstractCachingPageViewCreator<View> {

        @Override
        public View getDispatchedView(final ViewGroup parentView) {
            view = getLayoutInflater().inflate(R.layout.cachedetail_images_page, parentView, false);
            return view;
        }
    }

    private void loadTrackableImages() {
        if (imagesList != null) {
            return;
        }
        final PageViewCreator creator = getViewCreator(Page.IMAGES);
        if (creator == null) {
            return;
        }
        final View imageView = creator.getView(null);
        if (imageView == null) {
            return;
        }
        imagesList = new ImagesList(this, trackable.getGeocode());
        createSubscriptions.add(imagesList.loadImages(imageView, trackable.getImages(), false));
    }

    /**
     * Start loading images only when on images tab
     */
    private void lazyLoadTrackableImages() {
        if (isCurrentPage(Page.IMAGES)) {
            loadTrackableImages();
        }
    }

    @Override
    protected String getTitle(final Page page) {
        return res.getString(page.resId);
    }

    @Override
    protected Pair<List<? extends Page>, Integer> getOrderedPages() {
        final List<Page> pages = new ArrayList<>();
        pages.add(Page.DETAILS);
        if (CollectionUtils.isNotEmpty(trackable.getLogs())) {
            pages.add(Page.LOGS);
        }
        if (CollectionUtils.isNotEmpty(trackable.getImages())) {
            pages.add(Page.IMAGES);
        }
        return new ImmutablePair<List<? extends Page>, Integer>(pages, 0);
    }

    public class DetailsViewCreator extends AbstractCachingPageViewCreator<ScrollView> {

        @Bind(R.id.goal_box) protected LinearLayout goalBox;
        @Bind(R.id.goal) protected TextView goalTextView;
        @Bind(R.id.details_box) protected LinearLayout detailsBox;
        @Bind(R.id.details) protected TextView detailsTextView;
        @Bind(R.id.image_box) protected LinearLayout imageBox;
        @Bind(R.id.details_list) protected LinearLayout detailsList;
        @Bind(R.id.image) protected LinearLayout imageView;

        @Override
        public ScrollView getDispatchedView(final ViewGroup parentView) {
            view = (ScrollView) getLayoutInflater().inflate(R.layout.trackable_details_view, parentView, false);
            ButterKnife.bind(this, view);

            final CacheDetailsCreator details = new CacheDetailsCreator(TrackableActivity.this, detailsList);

            // action bar icon
            if (StringUtils.isNotBlank(trackable.getIconUrl())) {
                setupIcon(getSupportActionBar(), trackable.getIconUrl());
            } else {
                setupIcon(getSupportActionBar(), trackable.getIconBrand());
            }

            // trackable name
            final TextView name = details.add(R.string.trackable_name, StringUtils.isNotBlank(trackable.getName()) ? Html.fromHtml(trackable.getName()).toString() : res.getString(R.string.trackable_unknown)).right;
            addContextMenu(name);

            // missing status
            if (trackable.isMissing()) {
                name.setPaintFlags(name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            }

            // trackable type
            final String tbType;
            if (StringUtils.isNotBlank(trackable.getType())) {
                tbType = Html.fromHtml(trackable.getType()).toString();
            } else {
                tbType = res.getString(R.string.trackable_unknown);
            }
            details.add(R.string.trackable_type, tbType);

            // trackable geocode
            addContextMenu(details.add(R.string.trackable_code, trackable.getGeocode()).right);

            // trackable owner
            final TextView owner = details.add(R.string.trackable_owner, res.getString(R.string.trackable_unknown)).right;
            if (StringUtils.isNotBlank(trackable.getOwner())) {
                owner.setText(Html.fromHtml(trackable.getOwner()), TextView.BufferType.SPANNABLE);
                owner.setOnClickListener(new UserActionsClickListener(trackable));
            }

            // trackable spotted
            if (StringUtils.isNotBlank(trackable.getSpottedName()) ||
                    trackable.getSpottedType() == Trackable.SPOTTED_UNKNOWN ||
                    trackable.getSpottedType() == Trackable.SPOTTED_OWNER ||
                    trackable.getSpottedType() == Trackable.SPOTTED_ARCHIVED ||
                    trackable.getSpottedType() == Trackable.SPOTTED_TRAVELLING) {

                final StringBuilder text;
                boolean showTimeSpan = true;
                switch (trackable.getSpottedType()) {
                    case Trackable.SPOTTED_CACHE:
                        // TODO: the whole sentence fragment should not be constructed, but taken from the resources
                        text = new StringBuilder(res.getString(R.string.trackable_spotted_in_cache)).append(' ').append(Html.fromHtml(trackable.getSpottedName()));
                        break;
                    case Trackable.SPOTTED_USER:
                        // TODO: the whole sentence fragment should not be constructed, but taken from the resources
                        text = new StringBuilder(res.getString(R.string.trackable_spotted_at_user)).append(' ').append(Html.fromHtml(trackable.getSpottedName()));
                        break;
                    case Trackable.SPOTTED_UNKNOWN:
                        text = new StringBuilder(res.getString(R.string.trackable_spotted_unknown_location));
                        break;
                    case Trackable.SPOTTED_OWNER:
                        text = new StringBuilder(res.getString(R.string.trackable_spotted_owner));
                        break;
                    case Trackable.SPOTTED_ARCHIVED:
                        text = new StringBuilder(res.getString(R.string.trackable_spotted_archived));
                        break;
                    case Trackable.SPOTTED_TRAVELLING:
                        text = new StringBuilder(res.getString(R.string.trackable_spotted_travelling));
                        break;
                    default:
                        text = new StringBuilder("N/A");
                        showTimeSpan = false;
                        break;
                }

                // days since last spotting
                if (showTimeSpan) {
                    for (final LogEntry log : trackable.getLogs()) {
                        if (log.type == LogType.RETRIEVED_IT || log.type == LogType.GRABBED_IT || log.type == LogType.DISCOVERED_IT || log.type == LogType.PLACED_IT) {
                            text.append(" (").append(Formatter.formatDaysAgo(log.date)).append(')');
                            break;
                        }
                    }
                }

                final TextView spotted = details.add(R.string.trackable_spotted, text.toString()).right;
                spotted.setClickable(true);
                if (Trackable.SPOTTED_CACHE == trackable.getSpottedType()) {
                    spotted.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(final View arg0) {
                            if (StringUtils.isNotBlank(trackable.getSpottedGuid())) {
                                CacheDetailActivity.startActivityGuid(TrackableActivity.this, trackable.getSpottedGuid(), trackable.getSpottedName());
                            }
                            else {
                                // for GeoKrety we only know the cache geocode
                                final String cacheCode = trackable.getSpottedName();
                                if (ConnectorFactory.canHandle(cacheCode)) {
                                    CacheDetailActivity.startActivity(TrackableActivity.this, cacheCode);
                                }
                            }
                        }
                    });
                } else if (Trackable.SPOTTED_USER == trackable.getSpottedType()) {
                    spotted.setOnClickListener(new UserNameClickListener(trackable, Html.fromHtml(trackable.getSpottedName()).toString()));
                } else if (Trackable.SPOTTED_OWNER == trackable.getSpottedType()) {
                    spotted.setOnClickListener(new UserNameClickListener(trackable, Html.fromHtml(trackable.getOwner()).toString()));
                }
            }

            // trackable origin
            if (StringUtils.isNotBlank(trackable.getOrigin())) {
                final TextView origin = details.add(R.string.trackable_origin, "").right;
                origin.setText(Html.fromHtml(trackable.getOrigin()), TextView.BufferType.SPANNABLE);
                addContextMenu(origin);
            }

            // trackable released
            final Date releasedDate = trackable.getReleased();
            if (releasedDate != null) {
                addContextMenu(details.add(R.string.trackable_released, Formatter.formatDate(releasedDate.getTime())).right);
            }

            // trackable distance
            if (trackable.getDistance() >= 0) {
                addContextMenu(details.add(R.string.trackable_distance, Units.getDistanceFromKilometers(trackable.getDistance())).right);
            }

            // trackable goal
            if (StringUtils.isNotBlank(HtmlUtils.extractText(trackable.getGoal()))) {
                goalBox.setVisibility(View.VISIBLE);
                goalTextView.setVisibility(View.VISIBLE);
                goalTextView.setText(Html.fromHtml(trackable.getGoal(), new HtmlImage(geocode, true, 0, false, goalTextView), null), TextView.BufferType.SPANNABLE);
                goalTextView.setMovementMethod(AnchorAwareLinkMovementMethod.getInstance());
                addContextMenu(goalTextView);
            }

            // trackable details
            if (StringUtils.isNotBlank(HtmlUtils.extractText(trackable.getDetails()))) {
                detailsBox.setVisibility(View.VISIBLE);
                detailsTextView.setVisibility(View.VISIBLE);
                detailsTextView.setText(Html.fromHtml(trackable.getDetails(), new HtmlImage(geocode, true, 0, false, detailsTextView), new UnknownTagsHandler()), TextView.BufferType.SPANNABLE);
                detailsTextView.setMovementMethod(AnchorAwareLinkMovementMethod.getInstance());
                addContextMenu(detailsTextView);
            }

            // trackable image
            if (StringUtils.isNotBlank(trackable.getImage())) {
                imageBox.setVisibility(View.VISIBLE);
                final ImageView trackableImage = (ImageView) inflater.inflate(R.layout.trackable_image, imageView, false);

                trackableImage.setImageResource(R.drawable.image_not_loaded);
                trackableImage.setClickable(true);
                ViewObservable.clicks(trackableImage, false).subscribe(new Action1<OnClickEvent>() {
                    @Override
                    public void call(final OnClickEvent onClickEvent) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(trackable.getImage())));
                    }
                });

                AppObservable.bindActivity(TrackableActivity.this, new HtmlImage(geocode, true, 0, false).fetchDrawable(trackable.getImage())).subscribe(new Action1<BitmapDrawable>() {
                    @Override
                    public void call(final BitmapDrawable bitmapDrawable) {
                        trackableImage.setImageDrawable(bitmapDrawable);
                    }
                });

                imageView.addView(trackableImage);
            }
            return view;
        }

    }

    @Override
    public void addContextMenu(final View view) {
        view.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(final View v) {
                return startContextualActionBar(view);
            }
        });

        view.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(final View v) {
                startContextualActionBar(view);
            }
        });
    }

    private boolean startContextualActionBar(final View view) {
        if (currentActionMode != null) {
            return false;
        }
        currentActionMode = startSupportActionMode(new ActionMode.Callback() {

            @Override
            public boolean onPrepareActionMode(final ActionMode actionMode, final Menu menu) {
                return prepareClipboardActionMode(view, actionMode, menu);
            }

            private boolean prepareClipboardActionMode(final View view, final ActionMode actionMode, final Menu menu) {
                final int viewId = view.getId();
                assert view instanceof TextView;
                clickedItemText = ((TextView) view).getText();
                switch (viewId) {
                    case R.id.value: // name, TB-code, origin, released, distance
                        final TextView textView = ButterKnife.findById(((View) view.getParent()), R.id.name);
                        final CharSequence itemTitle = textView.getText();
                        buildDetailsContextMenu(actionMode, menu, itemTitle, true);
                        return true;
                    case R.id.goal:
                        buildDetailsContextMenu(actionMode, menu, res.getString(R.string.trackable_goal), false);
                        return true;
                    case R.id.details:
                        buildDetailsContextMenu(actionMode, menu, res.getString(R.string.trackable_details), false);
                        return true;
                    case R.id.log:
                        buildDetailsContextMenu(actionMode, menu, res.getString(R.string.cache_logs), false);
                        return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(final ActionMode actionMode) {
                currentActionMode = null;
            }

            @Override
            public boolean onCreateActionMode(final ActionMode actionMode, final Menu menu) {
                actionMode.getMenuInflater().inflate(R.menu.details_context, menu);
                prepareClipboardActionMode(view, actionMode, menu);
                return true;
            }

            @Override
            public boolean onActionItemClicked(final ActionMode actionMode, final MenuItem menuItem) {
                return onClipboardItemSelected(actionMode, menuItem, clickedItemText);
            }
        });
        return false;
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        // Refresh the logs view after coming back from logging a trackable
        if (requestCode == LogTrackableActivity.LOG_TRACKABLE && resultCode == RESULT_OK) {
            refreshTrackable(StringUtils.defaultIfBlank(trackable.getName(), trackable.getGeocode()));
        }
    }

    @Override
    protected void onDestroy() {
        createSubscriptions.unsubscribe();
        super.onDestroy();
    }

    public Trackable getTrackable() {
        return trackable;
    }
}
