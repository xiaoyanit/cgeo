package cgeo.geocaching;

import cgeo.geocaching.activity.AbstractActivity;
import cgeo.geocaching.activity.AbstractListActivity;
import cgeo.geocaching.activity.ActivityMixin;
import cgeo.geocaching.activity.FilteredActivity;
import cgeo.geocaching.activity.Progress;
import cgeo.geocaching.activity.ShowcaseViewBuilder;
import cgeo.geocaching.apps.cachelist.CacheListApp;
import cgeo.geocaching.apps.cachelist.CacheListAppUtils;
import cgeo.geocaching.apps.cachelist.CacheListApps;
import cgeo.geocaching.apps.cachelist.ListNavigationSelectionActionProvider;
import cgeo.geocaching.apps.navi.NavigationAppFactory;
import cgeo.geocaching.command.DeleteCachesCommand;
import cgeo.geocaching.command.DeleteListCommand;
import cgeo.geocaching.command.MoveToListCommand;
import cgeo.geocaching.command.RenameListCommand;
import cgeo.geocaching.compatibility.Compatibility;
import cgeo.geocaching.connector.gc.RecaptchaHandler;
import cgeo.geocaching.enumerations.CacheListType;
import cgeo.geocaching.enumerations.CacheType;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.enumerations.StatusCode;
import cgeo.geocaching.export.FieldnoteExport;
import cgeo.geocaching.export.GpxExport;
import cgeo.geocaching.files.GPXImporter;
import cgeo.geocaching.filter.FilterActivity;
import cgeo.geocaching.filter.IFilter;
import cgeo.geocaching.list.AbstractList;
import cgeo.geocaching.list.ListNameMemento;
import cgeo.geocaching.list.PseudoList;
import cgeo.geocaching.list.StoredList;
import cgeo.geocaching.loaders.AbstractSearchLoader;
import cgeo.geocaching.loaders.AbstractSearchLoader.CacheListLoaderType;
import cgeo.geocaching.loaders.CoordsGeocacheListLoader;
import cgeo.geocaching.loaders.FinderGeocacheListLoader;
import cgeo.geocaching.loaders.HistoryGeocacheListLoader;
import cgeo.geocaching.loaders.KeywordGeocacheListLoader;
import cgeo.geocaching.loaders.NextPageGeocacheListLoader;
import cgeo.geocaching.loaders.OfflineGeocacheListLoader;
import cgeo.geocaching.loaders.OwnerGeocacheListLoader;
import cgeo.geocaching.loaders.PocketGeocacheListLoader;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.maps.CGeoMap;
import cgeo.geocaching.network.Cookies;
import cgeo.geocaching.network.DownloadProgress;
import cgeo.geocaching.network.Network;
import cgeo.geocaching.network.Send2CgeoDownloader;
import cgeo.geocaching.sensors.GeoData;
import cgeo.geocaching.sensors.GeoDirHandler;
import cgeo.geocaching.sensors.Sensors;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.settings.SettingsActivity;
import cgeo.geocaching.sorting.CacheComparator;
import cgeo.geocaching.sorting.SortActionProvider;
import cgeo.geocaching.ui.CacheListAdapter;
import cgeo.geocaching.ui.LoggingUI;
import cgeo.geocaching.ui.WeakReferenceHandler;
import cgeo.geocaching.ui.dialog.Dialogs;
import cgeo.geocaching.utils.AngleUtils;
import cgeo.geocaching.utils.CalendarUtils;
import cgeo.geocaching.utils.CancellableHandler;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.RxUtils;

import com.github.amlcurran.showcaseview.targets.ActionViewTarget;
import com.github.amlcurran.showcaseview.targets.ActionViewTarget.Type;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.OpenableColumns;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import butterknife.ButterKnife;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class CacheListActivity extends AbstractListActivity implements FilteredActivity, LoaderManager.LoaderCallbacks<SearchResult> {

    private static final int MAX_LIST_ITEMS = 1000;

    private static final int REQUEST_CODE_IMPORT_GPX = 1;

    private static final String STATE_FILTER = "currentFilter";
    private static final String STATE_INVERSE_SORT = "currentInverseSort";
    private static final String STATE_LIST_TYPE = "currentListType";
    private static final String STATE_LIST_ID = "currentListId";

    private CacheListType type = null;
    private Geopoint coords = null;
    private SearchResult search = null;
    /** The list of shown caches shared with Adapter. Don't manipulate outside of main thread only with Handler */
    private final List<Geocache> cacheList = new ArrayList<>();
    private CacheListAdapter adapter = null;
    private View listFooter = null;
    private TextView listFooterText = null;
    private final Progress progress = new Progress();
    private String title = "";
    private int detailTotal = 0;
    private final AtomicInteger detailProgress = new AtomicInteger(0);
    private long detailProgressTime = 0L;
    private int listId = StoredList.TEMPORARY_LIST.id; // Only meaningful for the OFFLINE type
    private final GeoDirHandler geoDirHandler = new GeoDirHandler() {

        @Override
        public void updateDirection(final float direction) {
            if (Settings.isLiveList()) {
                adapter.setActualHeading(AngleUtils.getDirectionNow(direction));
            }
        }

        @Override
        public void updateGeoData(final GeoData geoData) {
            adapter.setActualCoordinates(geoData.getCoords());
        }

    };
    private ContextMenuInfo lastMenuInfo;
    private String contextMenuGeocode = "";
    private Subscription resumeSubscription;
    private final ListNameMemento listNameMemento = new ListNameMemento();

    // FIXME: This method has mostly been replaced by the loaders. But it still contains a license agreement check.
    public void handleCachesLoaded() {
        try {
            updateAdapter();

            updateTitle();

            showFooterMoreCaches();

            if (search != null && search.getError() == StatusCode.UNAPPROVED_LICENSE) {
                showLicenseConfirmationDialog();
            } else if (search != null && search.getError() != null) {
                showToast(res.getString(R.string.err_download_fail) + ' ' + search.getError().getErrorString(res) + '.');

                hideLoading();
                showProgress(false);

                finish();
                return;
            }

            setAdapterCurrentCoordinates(false);
        } catch (final Exception e) {
            showToast(res.getString(R.string.err_detail_cache_find_any));
            Log.e("CacheListActivity.loadCachesHandler", e);

            hideLoading();
            showProgress(false);

            finish();
            return;
        }

        try {
            hideLoading();
            showProgress(false);
        } catch (final Exception e2) {
            Log.e("CacheListActivity.loadCachesHandler.2", e2);
        }

        adapter.setSelectMode(false);
    }

    private void showLicenseConfirmationDialog() {
        final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(res.getString(R.string.license));
        dialog.setMessage(res.getString(R.string.err_license));
        dialog.setCancelable(true);
        dialog.setNegativeButton(res.getString(R.string.license_dismiss), new DialogInterface.OnClickListener() {

            @Override
            public void onClick(final DialogInterface dialog, final int id) {
                Cookies.clearCookies();
                dialog.cancel();
            }
        });
        dialog.setPositiveButton(res.getString(R.string.license_show), new DialogInterface.OnClickListener() {

            @Override
            public void onClick(final DialogInterface dialog, final int id) {
                Cookies.clearCookies();
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.geocaching.com/software/agreement.aspx?ID=0")));
            }
        });

        final AlertDialog alert = dialog.create();
        alert.show();
    }

    private final Handler loadCachesHandler = new LoadCachesHandler(this);

    private static class LoadCachesHandler extends WeakReferenceHandler<CacheListActivity> {

        protected LoadCachesHandler(final CacheListActivity activity) {
            super(activity);
        }

        @Override
        public void handleMessage(final Message msg) {
            final CacheListActivity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.handleCachesLoaded();
        }
    }

    /**
     * Loads the caches and fills the {@link #cacheList} according to {@link #search} content.
     *
     * If {@link #search} is <code>null</code>, this does nothing.
     */

    private void replaceCacheListFromSearch() {
        if (search != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    cacheList.clear();

                    // The database search was moved into the UI call intentionally. If this is done before the runOnUIThread,
                    // then we have 2 sets of caches in memory. This can lead to OOM for huge cache lists.
                    final Set<Geocache> cachesFromSearchResult = search.getCachesFromSearchResult(LoadFlags.LOAD_CACHE_OR_DB);

                    cacheList.addAll(cachesFromSearchResult);
                    adapter.reFilter();
                    updateTitle();
                    showFooterMoreCaches();
                }
            });
        }
    }

    private static String getCacheNumberString(final Resources res, final int count) {
        return res.getQuantityString(R.plurals.cache_counts, count, count);
    }

    protected void updateTitle() {
        setTitle(title);
        getSupportActionBar().setSubtitle(getCurrentSubtitle());
        refreshSpinnerAdapter();
    }

    private class LoadDetailsHandler extends CancellableHandler {

        @Override
        public void handleRegularMessage(final Message msg) {
            updateAdapter();

            if (msg.what == DownloadProgress.MSG_LOADED) {
                ((Geocache) msg.obj).setStatusChecked(false);

                adapter.notifyDataSetChanged();

                final int dp = detailProgress.get();
                final int secondsElapsed = (int) ((System.currentTimeMillis() - detailProgressTime) / 1000);
                final int minutesRemaining = ((detailTotal - dp) * secondsElapsed / ((dp > 0) ? dp : 1) / 60);

                progress.setProgress(dp);
                if (minutesRemaining < 1) {
                    progress.setMessage(res.getString(R.string.caches_downloading) + " " + res.getString(R.string.caches_eta_ltm));
                } else {
                    progress.setMessage(res.getString(R.string.caches_downloading) + " " + res.getQuantityString(R.plurals.caches_eta_mins, minutesRemaining, minutesRemaining));
                }
            } else {
                new AsyncTask<Void, Void, Set<Geocache>>() {
                    @Override
                    protected Set<Geocache> doInBackground(final Void... params) {
                        return search != null ? search.getCachesFromSearchResult(LoadFlags.LOAD_CACHE_OR_DB) : null;
                    }

                    @Override
                    protected void onPostExecute(final Set<Geocache> result) {
                        if (CollectionUtils.isNotEmpty(result)) {
                            cacheList.clear();
                            cacheList.addAll(result);
                            adapter.reFilter();
                        }
                        setAdapterCurrentCoordinates(false);

                        showProgress(false);
                        progress.dismiss();
                    }
                }.execute();
            }
        }
    }

    /**
     * TODO Possibly parts should be a Thread not a Handler
     */
    private class DownloadFromWebHandler extends CancellableHandler {
        @Override
        public void handleRegularMessage(final Message msg) {
            updateAdapter();

            adapter.notifyDataSetChanged();

            switch (msg.what) {
                case DownloadProgress.MSG_WAITING:  //no caches
                    progress.setMessage(res.getString(R.string.web_import_waiting));
                    break;
                case DownloadProgress.MSG_LOADING:  //cache downloading
                    progress.setMessage(res.getString(R.string.web_downloading) + " " + msg.obj + '…');
                    break;
                case DownloadProgress.MSG_LOADED:  //Cache downloaded
                    progress.setMessage(res.getString(R.string.web_downloaded) + " " + msg.obj + '…');
                    refreshCurrentList();
                    break;
                case DownloadProgress.MSG_SERVER_FAIL:
                    progress.dismiss();
                    showToast(res.getString(R.string.sendToCgeo_download_fail));
                    finish();
                    break;
                case DownloadProgress.MSG_NO_REGISTRATION:
                    progress.dismiss();
                    showToast(res.getString(R.string.sendToCgeo_no_registration));
                    finish();
                    break;
                default:  // MSG_DONE
                    adapter.setSelectMode(false);
                    replaceCacheListFromSearch();
                    progress.dismiss();
                    break;
            }
        }
    }

    private final CancellableHandler clearOfflineLogsHandler = new CancellableHandler() {

        @Override
        public void handleRegularMessage(final Message msg) {
            adapter.setSelectMode(false);

            refreshCurrentList();

            replaceCacheListFromSearch();

            progress.dismiss();
        }
    };

    private final Handler importGpxAttachementFinishedHandler = new Handler() {
        @Override
        public void handleMessage(final Message msg) {
            refreshCurrentList();
        }
    };
    private AbstractSearchLoader currentLoader;

    public CacheListActivity() {
        super(true);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme();

        setContentView(R.layout.cacheslist_activity);

        // get parameters
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            type = Intents.getListType(getIntent());
            coords = extras.getParcelable(Intents.EXTRA_COORDS);
        }
        else {
            extras = new Bundle();
        }
        if (isInvokedFromAttachment()) {
            type = CacheListType.OFFLINE;
            if (coords == null) {
                coords = Geopoint.ZERO;
            }
        }
        if (type == CacheListType.NEAREST) {
            coords = Sensors.getInstance().currentGeo().getCoords();
        }

        setTitle(title);

        // Check whether we're recreating a previously destroyed instance
        if (savedInstanceState != null) {
            // Restore value of members from saved state
            currentFilter = savedInstanceState.getParcelable(STATE_FILTER);
            currentInverseSort = savedInstanceState.getBoolean(STATE_INVERSE_SORT);
            type = CacheListType.values()[savedInstanceState.getInt(STATE_LIST_TYPE, type.ordinal())];
            listId = savedInstanceState.getInt(STATE_LIST_ID);
        }

        initAdapter();

        prepareFilterBar();

        if (type.canSwitch) {
            initActionBarSpinner();
        }

        currentLoader = (AbstractSearchLoader) getSupportLoaderManager().initLoader(type.getLoaderId(), extras, this);

        // init
        if (CollectionUtils.isNotEmpty(cacheList)) {
            // currentLoader can be null if this activity is created from a map, as onCreateLoader() will return null.
            if (currentLoader != null && currentLoader.isStarted()) {
                showFooterLoadingCaches();
            } else {
                showFooterMoreCaches();
            }
        }

        if (isInvokedFromAttachment()) {
            importGpxAttachement();
        }
        else {
            presentShowcase();
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);

        // Save the current Filter
        savedInstanceState.putParcelable(STATE_FILTER, currentFilter);
        savedInstanceState.putBoolean(STATE_INVERSE_SORT, adapter.getInverseSort());
        savedInstanceState.putInt(STATE_LIST_TYPE, type.ordinal());
        savedInstanceState.putInt(STATE_LIST_ID, listId);
    }

    /**
     * Action bar spinner adapter. {@code null} for list types that don't allow switching (search results, ...).
     */
    CacheListSpinnerAdapter mCacheListSpinnerAdapter;

    /**
     * remember current filter when switching between lists, so it can be re-applied afterwards
     */
    private IFilter currentFilter = null;
    private boolean currentInverseSort = false;

    private SortActionProvider sortProvider;

    private void initActionBarSpinner() {
        mCacheListSpinnerAdapter = new CacheListSpinnerAdapter(this, R.layout.support_simple_spinner_dropdown_item);
        getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setListNavigationCallbacks(mCacheListSpinnerAdapter, new ActionBar.OnNavigationListener() {
            @Override
            public boolean onNavigationItemSelected(final int i, final long l) {
                final int newListId = mCacheListSpinnerAdapter.getItem(i).id;
                if (newListId != listId) {
                    switchListById(newListId);
                }
                return true;
            }
        });
    }

    private void refreshSpinnerAdapter() {
        /* If the activity does not use the Spinner this will be null */
        if (mCacheListSpinnerAdapter==null) {
            return;
        }
        mCacheListSpinnerAdapter.clear();

        final AbstractList list = AbstractList.getListById(listId);

        for (final AbstractList l: StoredList.UserInterface.getMenuLists(false, PseudoList.NEW_LIST.id)) {
            mCacheListSpinnerAdapter.add(l);
        }

        getSupportActionBar().setSelectedNavigationItem(mCacheListSpinnerAdapter.getPosition(list));
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (currentLoader != null && currentLoader.isLoading()) {
            showFooterLoadingCaches();
        }
    }

    private boolean isConcreteList() {
        return type == CacheListType.OFFLINE &&
                (listId == StoredList.STANDARD_LIST_ID || listId >= DataStore.customListIdOffset);
    }

    private boolean isInvokedFromAttachment() {
        final Intent intent = getIntent();
        return Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null;
    }

    private void importGpxAttachement() {
        new StoredList.UserInterface(this).promptForListSelection(R.string.gpx_import_select_list_title, new Action1<Integer>() {

            @Override
            public void call(final Integer listId) {
                new GPXImporter(CacheListActivity.this, listId, importGpxAttachementFinishedHandler).importGPX();
                switchListById(listId);
            }
        }, true, 0);
    }

    @Override
    public void onResume() {
        super.onResume();

        resumeSubscription = geoDirHandler.start(GeoDirHandler.UPDATE_GEODATA | GeoDirHandler.UPDATE_DIRECTION | GeoDirHandler.LOW_POWER, 250, TimeUnit.MILLISECONDS);

        adapter.setSelectMode(false);
        setAdapterCurrentCoordinates(true);

        if (search != null) {
            replaceCacheListFromSearch();
            loadCachesHandler.sendEmptyMessage(0);
        }

        // refresh standard list if it has changed (new caches downloaded)
        if (type == CacheListType.OFFLINE && (listId >= StoredList.STANDARD_LIST_ID || listId == PseudoList.ALL_LIST.id) && search != null) {
            final SearchResult newSearch = DataStore.getBatchOfStoredCaches(coords, Settings.getCacheType(), listId);
            if (newSearch.getTotalCountGC() != search.getTotalCountGC()) {
                refreshCurrentList();
            }
        }
    }

    private void setAdapterCurrentCoordinates(final boolean forceSort) {
        adapter.setActualCoordinates(Sensors.getInstance().currentGeo().getCoords());
        if (forceSort) {
            adapter.forceSort();
        }
    }

    @Override
    public void onPause() {
        resumeSubscription.unsubscribe();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.cache_list_options, menu);

        sortProvider = (SortActionProvider) MenuItemCompat.getActionProvider(menu.findItem(R.id.menu_sort));
        assert sortProvider != null;  // We set it in the XML file
        sortProvider.setSelection(adapter.getCacheComparator());
        sortProvider.setIsEventsOnly(adapter.isEventsOnly());
        sortProvider.setClickListener(new Action1<CacheComparator>() {

            @Override
            public void call(final CacheComparator selectedComparator) {
                final CacheComparator oldComparator = adapter.getCacheComparator();
                // selecting the same sorting twice will toggle the order
                if (selectedComparator != null && oldComparator != null && selectedComparator.getClass().equals(oldComparator.getClass())) {
                    adapter.toggleInverseSort();
                } else {
                    // always reset the inversion for a new sorting criteria
                    adapter.resetInverseSort();
                }
                setComparator(selectedComparator);
                sortProvider.setSelection(selectedComparator);
            }
        });

        ListNavigationSelectionActionProvider.initialize(menu.findItem(R.id.menu_cache_list_app_provider), new ListNavigationSelectionActionProvider.Callback() {

            @Override
            public void onListNavigationSelected(final CacheListApp app) {
                app.invoke(CacheListAppUtils.filterCoords(cacheList), CacheListActivity.this, getFilteredSearch());
            }
        });

        return true;
    }

    private static void setVisible(final Menu menu, final int itemId, final boolean visible) {
        menu.findItem(itemId).setVisible(visible);
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);

        final boolean isHistory = type == CacheListType.HISTORY;
        final boolean isOffline = type == CacheListType.OFFLINE;
        final boolean isEmpty = cacheList.isEmpty();
        final boolean isConcrete = isConcreteList();

        try {
            if (adapter.isSelectMode()) {
                menu.findItem(R.id.menu_switch_select_mode).setTitle(res.getString(R.string.caches_select_mode_exit))
                        .setIcon(R.drawable.ic_menu_clear_playlist);
            } else {
                menu.findItem(R.id.menu_switch_select_mode).setTitle(res.getString(R.string.caches_select_mode))
                        .setIcon(R.drawable.ic_menu_agenda);
            }
            menu.findItem(R.id.menu_invert_selection).setVisible(adapter.isSelectMode());

            setVisible(menu, R.id.menu_show_on_map, !isEmpty);
            setVisible(menu, R.id.menu_filter, search != null && search.getCount() > 0);
            setVisible(menu, R.id.menu_switch_select_mode, !isEmpty);
            setVisible(menu, R.id.menu_create_list, isOffline);

            setVisible(menu, R.id.menu_sort, !isEmpty && !isHistory);
            setVisible(menu, R.id.menu_refresh_stored, !isEmpty && (isConcrete || type != CacheListType.OFFLINE));
            setVisible(menu, R.id.menu_drop_caches, !isEmpty && isOffline);
            setVisible(menu, R.id.menu_delete_events, isConcrete && !isEmpty && containsPastEvents());
            setVisible(menu, R.id.menu_move_to_list, isOffline && !isEmpty);
            setVisible(menu, R.id.menu_remove_from_history, !isEmpty && isHistory);
            setVisible(menu, R.id.menu_clear_offline_logs, !isEmpty && (isHistory || isOffline) && containsOfflineLogs());
            setVisible(menu, R.id.menu_import, isOffline);
            setVisible(menu, R.id.menu_import_web, isOffline);
            setVisible(menu, R.id.menu_import_gpx, isOffline);
            setVisible(menu, R.id.menu_export, !isEmpty);

            if (!isOffline && !isHistory) {
                menu.findItem(R.id.menu_refresh_stored).setTitle(R.string.caches_store_offline);
            }

            final boolean isNonDefaultList = isConcrete && listId != StoredList.STANDARD_LIST_ID;

            if (isOffline || type == CacheListType.HISTORY) { // only offline list
                setMenuItemLabel(menu, R.id.menu_drop_caches, R.string.caches_remove_selected, R.string.caches_remove_all);
                setMenuItemLabel(menu, R.id.menu_refresh_stored, R.string.caches_refresh_selected, R.string.caches_refresh_all);
                setMenuItemLabel(menu, R.id.menu_move_to_list, R.string.caches_move_selected, R.string.caches_move_all);
            } else { // search and global list (all other than offline and history)
                setMenuItemLabel(menu, R.id.menu_refresh_stored, R.string.caches_store_selected, R.string.caches_store_offline);
            }

            menu.findItem(R.id.menu_drop_list).setVisible(isNonDefaultList);
            menu.findItem(R.id.menu_rename_list).setVisible(isNonDefaultList);

            setMenuItemLabel(menu, R.id.menu_remove_from_history, R.string.cache_remove_from_history, R.string.cache_clear_history);
            menu.findItem(R.id.menu_import_android).setVisible(Compatibility.isStorageAccessFrameworkAvailable() && isOffline);

            final List<CacheListApp> listNavigationApps = CacheListApps.getActiveApps();
            menu.findItem(R.id.menu_cache_list_app_provider).setVisible(!isEmpty && listNavigationApps.size() > 1);
            menu.findItem(R.id.menu_cache_list_app).setVisible(!isEmpty && listNavigationApps.size() == 1);

        } catch (final RuntimeException e) {
            Log.e("CacheListActivity.onPrepareOptionsMenu", e);
        }

        return true;
    }

    private boolean containsPastEvents() {
        for (final Geocache cache : adapter.getCheckedOrAllCaches()) {
            if (CalendarUtils.isPastEvent(cache)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsOfflineLogs() {
        for (final Geocache cache : adapter.getCheckedOrAllCaches()) {
            if (cache.isLogOffline()) {
                return true;
            }
        }
        return false;
    }

    private void setMenuItemLabel(final Menu menu, final int menuId, final int resIdSelection, final int resId) {
        final MenuItem menuItem = menu.findItem(menuId);
        if (menuItem == null) {
            return;
        }
        final boolean hasSelection = adapter != null && adapter.getCheckedCount() > 0;
        if (hasSelection) {
            menuItem.setTitle(res.getString(resIdSelection) + " (" + adapter.getCheckedCount() + ")");
        } else {
            menuItem.setTitle(res.getString(resId));
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_show_on_map:
                goMap();
                return true;
            case R.id.menu_switch_select_mode:
                adapter.switchSelectMode();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_refresh_stored:
                refreshStored(adapter.getCheckedOrAllCaches());
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_drop_caches:
                deleteCachesWithConfirmation();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_import_gpx:
                importGpx();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_import_android:
                importGpxFromAndroid();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_create_list:
                new StoredList.UserInterface(this).promptForListCreation(getListSwitchingRunnable(), listNameMemento.getTerm());
                refreshSpinnerAdapter();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_drop_list:
                removeList();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_rename_list:
                renameList();
                return true;
            case R.id.menu_invert_selection:
                adapter.invertSelection();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_filter:
                showFilterMenu(null);
                return true;
            case R.id.menu_import_web:
                importWeb();
                return true;
            case R.id.menu_export_gpx:
                new GpxExport().export(adapter.getCheckedOrAllCaches(), this);
                return true;
            case R.id.menu_export_fieldnotes:
                new FieldnoteExport().export(adapter.getCheckedOrAllCaches(), this);
                return true;
            case R.id.menu_remove_from_history:
                removeFromHistoryCheck();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_move_to_list:
                moveCachesToOtherList(adapter.getCheckedOrAllCaches());
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_delete_events:
                deletePastEvents();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_clear_offline_logs:
                clearOfflineLogs();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_cache_list_app:
                if (cacheToShow()) {
                    CacheListApps.getActiveApps().get(0).invoke(CacheListAppUtils.filterCoords(cacheList), this, getFilteredSearch());
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean cacheToShow() {
        if (search == null || CollectionUtils.isEmpty(cacheList)) {
            showToast(res.getString(R.string.warn_no_cache_coord));
            return false;
        }
        return true;
    }

    private SearchResult getFilteredSearch() {
        return new SearchResult(Geocache.getGeocodes(adapter.getFilteredList()));
    }

    private void deletePastEvents() {
        final List<Geocache> deletion = new ArrayList<>();
        for (final Geocache cache : adapter.getCheckedOrAllCaches()) {
            if (CalendarUtils.isPastEvent(cache)) {
                deletion.add(cache);
            }
        }
        deleteCachesInternal(deletion);
    }

    private void clearOfflineLogs() {
        Dialogs.confirmYesNo(this, R.string.caches_clear_offlinelogs, R.string.caches_clear_offlinelogs_message, new OnClickListener() {

            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                progress.show(CacheListActivity.this, null, res.getString(R.string.caches_clear_offlinelogs_progress), true, clearOfflineLogsHandler.cancelMessage());
                clearOfflineLogs(clearOfflineLogsHandler, adapter.getCheckedOrAllCaches());
            }
        });
    }

    /**
     * called from the filter bar view
     */
    @Override
    public void showFilterMenu(final View view) {
        FilterActivity.selectFilter(this);
    }

    private void setComparator(final CacheComparator comparator) {
        adapter.setComparator(comparator);
        currentInverseSort = adapter.getInverseSort();
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View view, final ContextMenu.ContextMenuInfo info) {
        super.onCreateContextMenu(menu, view, info);

        AdapterContextMenuInfo adapterInfo = null;
        try {
            adapterInfo = (AdapterContextMenuInfo) info;
        } catch (final Exception e) {
            Log.w("CacheListActivity.onCreateContextMenu", e);
        }

        if (adapterInfo == null || adapterInfo.position >= adapter.getCount()) {
            return;
        }
        final Geocache cache = adapter.getItem(adapterInfo.position);

        menu.setHeaderTitle(StringUtils.defaultIfBlank(cache.getName(), cache.getGeocode()));

        contextMenuGeocode = cache.getGeocode();

        getMenuInflater().inflate(R.menu.cache_list_context, menu);

        menu.findItem(R.id.menu_default_navigation).setTitle(NavigationAppFactory.getDefaultNavigationApplication().getName());
        final boolean hasCoords = cache.getCoords() != null;
        menu.findItem(R.id.menu_default_navigation).setVisible(hasCoords);
        menu.findItem(R.id.menu_navigate).setVisible(hasCoords);
        menu.findItem(R.id.menu_cache_details).setVisible(hasCoords);
        final boolean isOffline = cache.isOffline();
        menu.findItem(R.id.menu_drop_cache).setVisible(isOffline);
        menu.findItem(R.id.menu_move_to_list).setVisible(isOffline);
        menu.findItem(R.id.menu_refresh).setVisible(isOffline);
        menu.findItem(R.id.menu_store_cache).setVisible(!isOffline);

        LoggingUI.onPrepareOptionsMenu(menu, cache);
    }

    private void moveCachesToOtherList(final Collection<Geocache> caches) {
        new MoveToListCommand(this, caches, listId) {

            @Override
            protected void onFinished() {
                adapter.setSelectMode(false);
                refreshCurrentList();
            }

        }.execute();
    }

    @Override
    public boolean onContextItemSelected(final MenuItem item) {
        ContextMenu.ContextMenuInfo info = item.getMenuInfo();

        // restore menu info for sub menu items, see
        // https://code.google.com/p/android/issues/detail?id=7139
        if (info == null) {
            info = lastMenuInfo;
            lastMenuInfo = null;
        }

        AdapterContextMenuInfo adapterInfo = null;
        try {
            adapterInfo = (AdapterContextMenuInfo) info;
        } catch (final Exception e) {
            Log.w("CacheListActivity.onContextItemSelected", e);
        }

        final Geocache cache = adapterInfo != null ? getCacheFromAdapter(adapterInfo) : null;

        // just in case the list got resorted while we are executing this code
        if (cache == null) {
            return true;
        }

        switch (item.getItemId()) {
            case R.id.menu_default_navigation:
                NavigationAppFactory.startDefaultNavigationApplication(1, this, cache);
                break;
            case R.id.menu_navigate:
                NavigationAppFactory.showNavigationMenu(this, cache, null, null);
                break;
            case R.id.menu_cache_details:
                CacheDetailActivity.startActivity(this, cache.getGeocode(), cache.getName());
                break;
            case R.id.menu_drop_cache:
                deleteCachesInternal(Collections.singletonList(cache));
                break;
            case R.id.menu_move_to_list:
                moveCachesToOtherList(Collections.singletonList(cache));
                break;
            case R.id.menu_store_cache:
            case R.id.menu_refresh:
                refreshStored(Collections.singletonList(cache));
                break;
            default:
                // we must remember the menu info for the sub menu, there is a bug
                // in Android:
                // https://code.google.com/p/android/issues/detail?id=7139
                lastMenuInfo = info;
                LoggingUI.onMenuItemSelected(item, this, cache);
        }

        return true;
    }

    /**
     * Extract a cache from adapter data.
     *
     * @param adapterInfo
     *            an adapterInfo
     * @return the pointed cache
     */
    private Geocache getCacheFromAdapter(final AdapterContextMenuInfo adapterInfo) {
        final Geocache cache = adapter.getItem(adapterInfo.position);
        if (cache.getGeocode().equalsIgnoreCase(contextMenuGeocode)) {
            return cache;
        }

        return adapter.findCacheByGeocode(contextMenuGeocode);
    }

    private boolean setFilter(final IFilter filter) {
        currentFilter = filter;
        adapter.setFilter(filter);
        prepareFilterBar();
        updateTitle();
        invalidateOptionsMenuCompatible();
        return true;
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (adapter.isSelectMode()) {
                adapter.setSelectMode(false);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void initAdapter() {
        final ListView listView = getListView();
        registerForContextMenu(listView);
        adapter = new CacheListAdapter(this, cacheList, type);
        adapter.setFilter(currentFilter);

        if (listFooter == null) {
            listFooter = getLayoutInflater().inflate(R.layout.cacheslist_footer, listView, false);
            listFooter.setClickable(true);
            listFooter.setOnClickListener(new MoreCachesListener());
            listFooterText = ButterKnife.findById(listFooter, R.id.more_caches);
            listView.addFooterView(listFooter);
        }
        setListAdapter(adapter);

        adapter.setInverseSort(currentInverseSort);
        adapter.forceSort();
    }

    private void updateAdapter() {
        adapter.notifyDataSetChanged();
        adapter.reFilter();
        adapter.checkEvents();
        adapter.forceSort();
    }

    private void showFooterLoadingCaches() {
        // no footer for offline lists
        if (listFooter == null) {
            return;
        }
        listFooterText.setText(res.getString(R.string.caches_more_caches_loading));
        listFooter.setClickable(false);
        listFooter.setOnClickListener(null);
    }

    private void showFooterMoreCaches() {
        // no footer in offline lists
        if (listFooter == null) {
            return;
        }

        boolean enableMore = type != CacheListType.OFFLINE && cacheList.size() < MAX_LIST_ITEMS;
        if (enableMore && search != null) {
            final int count = search.getTotalCountGC();
            enableMore = count > 0 && cacheList.size() < count;
        }

        listFooter.setClickable(enableMore);
        if (enableMore) {
            listFooterText.setText(res.getString(R.string.caches_more_caches) + " (" + res.getString(R.string.caches_more_caches_currently) + ": " + cacheList.size() + ")");
            listFooter.setOnClickListener(new MoreCachesListener());
        } else if (type != CacheListType.OFFLINE) {
            listFooterText.setText(res.getString(CollectionUtils.isEmpty(cacheList) ? R.string.caches_no_cache : R.string.caches_more_caches_no));
            listFooter.setOnClickListener(null);
        } else {
            // hiding footer for offline list is not possible, it must be removed instead
            // http://stackoverflow.com/questions/7576099/hiding-footer-in-listview
            getListView().removeFooterView(listFooter);
        }
    }

    private void importGpx() {
        GpxFileListActivity.startSubActivity(this, listId);
    }

    private void importGpxFromAndroid() {
        Compatibility.importGpxFromStorageAccessFramework(this, REQUEST_CODE_IMPORT_GPX);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_IMPORT_GPX && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.  Pull that uri using "resultData.getData()"
            if (data != null) {
                final Uri uri = data.getData();
                new GPXImporter(this, listId, importGpxAttachementFinishedHandler).importGPX(uri, null, getDisplayName(uri));
            }
        }
        else if (requestCode == FilterActivity.REQUEST_SELECT_FILTER && resultCode == Activity.RESULT_OK) {
            final int[] filterIndex = data.getIntArrayExtra(FilterActivity.EXTRA_FILTER_RESULT);
            setFilter(FilterActivity.getFilterFromPosition(filterIndex[0], filterIndex[1]));
        }

        if (type == CacheListType.OFFLINE) {
            refreshCurrentList();
        }
    }

    private String getDisplayName(final Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public void refreshStored(final List<Geocache> caches) {
        detailTotal = caches.size();
        if (detailTotal == 0) {
            return;
        }

        if (!Network.isNetworkConnected()) {
            showToast(getString(R.string.err_server));
            return;
        }

        if (Settings.getChooseList() && (type != CacheListType.OFFLINE && type != CacheListType.HISTORY)) {
            // let user select list to store cache in
            new StoredList.UserInterface(this).promptForListSelection(R.string.list_title,
                    new Action1<Integer>() {
                        @Override
                        public void call(final Integer selectedListId) {
                            // in case of online lists, set the list id to a concrete list now
                            for (final Geocache geocache : caches) {
                                geocache.setListId(selectedListId);
                            }
                            refreshStoredInternal(caches);
                        }
                    }, true, StoredList.TEMPORARY_LIST.id, listNameMemento);
        } else {
            if (type != CacheListType.OFFLINE) {
                for (final Geocache geocache : caches) {
                    if (geocache.getListId() == StoredList.TEMPORARY_LIST.id) {
                        geocache.setListId(StoredList.STANDARD_LIST_ID);
                    }
                }
            }
            refreshStoredInternal(caches);
        }
    }

    private void refreshStoredInternal(final List<Geocache> caches) {
        detailProgress.set(0);

        showProgress(false);

        final int etaTime = ((detailTotal * 25) / 60);
        final String message;
        if (etaTime < 1) {
            message = res.getString(R.string.caches_downloading) + " " + res.getString(R.string.caches_eta_ltm);
        } else {
            message = res.getString(R.string.caches_downloading) + " " + res.getQuantityString(R.plurals.caches_eta_mins, etaTime, etaTime);
        }

        final LoadDetailsHandler loadDetailsHandler = new LoadDetailsHandler();
        progress.show(this, null, message, ProgressDialog.STYLE_HORIZONTAL, loadDetailsHandler.cancelMessage());
        progress.setMaxProgressAndReset(detailTotal);

        detailProgressTime = System.currentTimeMillis();

        loadDetails(loadDetailsHandler, caches);
    }

    public void removeFromHistoryCheck() {
        final int message = (adapter != null && adapter.getCheckedCount() > 0) ? R.string.cache_remove_from_history
                : R.string.cache_clear_history;
        Dialogs.confirmYesNo(this, R.string.caches_removing_from_history, message, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int id) {
                removeFromHistory();
                dialog.cancel();
            }
        });
    }

    private void removeFromHistory() {
        final List<Geocache> caches = adapter.getCheckedOrAllCaches();
        final String[] geocodes = new String[caches.size()];
        for (int i = 0; i < geocodes.length; i++) {
            geocodes[i] = caches.get(i).getGeocode();
        }
        DataStore.clearVisitDate(geocodes);
        refreshCurrentList();
    }

    private void importWeb() {
        // menu is also shown with no device connected
        if (!Settings.isRegisteredForSend2cgeo()) {
            Dialogs.confirm(this, R.string.web_import_title, R.string.init_sendToCgeo_description, new OnClickListener() {

                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    SettingsActivity.openForScreen(R.string.preference_screen_sendtocgeo, CacheListActivity.this);
                }
            });
            return;
        }

        detailProgress.set(0);
        showProgress(false);
        final DownloadFromWebHandler downloadFromWebHandler = new DownloadFromWebHandler();
        progress.show(this, null, res.getString(R.string.web_import_waiting), true, downloadFromWebHandler.cancelMessage());
        Send2CgeoDownloader.loadFromWeb(downloadFromWebHandler, listId);
    }

    private void deleteCachesWithConfirmation() {
        final int titleId = (adapter.getCheckedCount() > 0) ? R.string.caches_remove_selected : R.string.caches_remove_all;
        final int count = adapter.getCheckedOrAllCount();
        final String message = res.getQuantityString(adapter.getCheckedCount() > 0 ? R.plurals.caches_remove_selected_confirm : R.plurals.caches_remove_all_confirm, count, count);
        Dialogs.confirmYesNo(this, titleId, message, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(final DialogInterface dialog, final int id) {
                deleteCachesInternal(adapter.getCheckedOrAllCaches());
                dialog.cancel();
            }
        });
    }

    private void deleteCachesInternal(final @NonNull Collection<Geocache> caches) {
        new DeleteCachesFromListCommand(CacheListActivity.this, caches).execute();
    }

    /**
     * Method to asynchronously refresh the caches details.
     */

    private void loadDetails(final CancellableHandler handler, final List<Geocache> caches) {
        final Observable<Geocache> allCaches;
        if (Settings.isStoreOfflineMaps()) {
            final List<Geocache> withStaticMaps = new ArrayList<>(caches.size());
            final List<Geocache> withoutStaticMaps = new ArrayList<>(caches.size());
            for (final Geocache cache : caches) {
                if (cache.hasStaticMap()) {
                    withStaticMaps.add(cache);
                } else {
                    withoutStaticMaps.add(cache);
                }
            }
            allCaches = Observable.concat(Observable.from(withoutStaticMaps), Observable.from(withStaticMaps));
        } else {
            allCaches = Observable.from(caches);
        }
        final Observable<Geocache> loaded = allCaches.flatMap(new Func1<Geocache, Observable<Geocache>>() {
            @Override
            public Observable<Geocache> call(final Geocache cache) {
                return Observable.create(new OnSubscribe<Geocache>() {
                    @Override
                    public void call(final Subscriber<? super Geocache> subscriber) {
                        cache.refreshSynchronous(null);
                        detailProgress.incrementAndGet();
                        handler.obtainMessage(DownloadProgress.MSG_LOADED, cache).sendToTarget();
                        subscriber.onCompleted();
                    }
                }).subscribeOn(RxUtils.refreshScheduler);
            }
        }).doOnCompleted(new Action0() {
            @Override
            public void call() {
                handler.sendEmptyMessage(DownloadProgress.MSG_DONE);
            }
        });
        handler.unsubscribeIfCancelled(loaded.subscribe());
    }

    private static final class DeleteCachesFromListCommand extends DeleteCachesCommand {

        private final WeakReference<CacheListActivity> activityRef;
        private final int lastListPosition;

        public DeleteCachesFromListCommand(@NonNull final CacheListActivity context, final Collection<Geocache> caches) {
            super(context, caches);
            lastListPosition = context.getListView().getFirstVisiblePosition();
            activityRef = new WeakReference<>(context);
        }

        @Override
        public void onFinished() {
            final CacheListActivity activity = activityRef.get();
            if (activity != null) {
                activity.adapter.setSelectMode(false);
                activity.refreshCurrentList();
                activity.replaceCacheListFromSearch();
                activity.getListView().setSelection(lastListPosition);
            }
        }

    }

    private static void clearOfflineLogs(final Handler handler, final List<Geocache> selectedCaches) {
        Schedulers.io().createWorker().schedule(new Action0() {
            @Override
            public void call() {
                DataStore.clearLogsOffline(selectedCaches);
                handler.sendEmptyMessage(DownloadProgress.MSG_DONE);
            }
        });
    }

    private class MoreCachesListener implements View.OnClickListener {

        @Override
        public void onClick(final View arg0) {
            showProgress(true);
            showFooterLoadingCaches();

            getSupportLoaderManager().restartLoader(CacheListLoaderType.NEXT_PAGE.getLoaderId(), null, CacheListActivity.this);
        }
    }

    private void hideLoading() {
        final ListView list = getListView();
        if (list.getVisibility() == View.GONE) {
            list.setVisibility(View.VISIBLE);
            final View loading = findViewById(R.id.loading);
            loading.setVisibility(View.GONE);
        }
    }

    @NonNull
    private Action1<Integer> getListSwitchingRunnable() {
        return new Action1<Integer>() {

            @Override
            public void call(final Integer selectedListId) {
                switchListById(selectedListId);
            }
        };
    }

    private void switchListById(final int id) {
        if (id < 0) {
            return;
        }

        if (id == PseudoList.HISTORY_LIST.id) {
            type = CacheListType.HISTORY;
            getSupportLoaderManager().destroyLoader(CacheListType.OFFLINE.getLoaderId());
            currentLoader = (AbstractSearchLoader) getSupportLoaderManager().restartLoader(CacheListType.HISTORY.getLoaderId(), null, this);
        } else {
            if (id == PseudoList.ALL_LIST.id) {
                listId = id;
                title = res.getString(R.string.list_all_lists);
            } else {
                final StoredList list = DataStore.getList(id);
                listId = list.id;
                title = list.title;
            }
            type = CacheListType.OFFLINE;

            getSupportLoaderManager().destroyLoader(CacheListType.HISTORY.getLoaderId());
            currentLoader = (OfflineGeocacheListLoader) getSupportLoaderManager().restartLoader(CacheListType.OFFLINE.getLoaderId(), OfflineGeocacheListLoader.getBundleForList(listId), this);

            Settings.saveLastList(listId);
        }

        initAdapter();

        showProgress(true);
        showFooterLoadingCaches();
        adapter.setSelectMode(false);
        invalidateOptionsMenuCompatible();
    }

    private void renameList() {
        (new RenameListCommand(this, listId) {

            @Override
            protected void onFinished() {
                refreshCurrentList();
            }
        }).execute();
    }

    private void removeListInternal() {
        new DeleteListCommand(this, listId) {

            private String oldListName;

            @Override
            protected boolean canExecute() {
                oldListName = DataStore.getList(listId).getTitle();
                return super.canExecute();
            }

            @Override
            protected void onFinished() {
                refreshSpinnerAdapter();
                switchListById(StoredList.STANDARD_LIST_ID);
            }

            @Override
            protected void onFinishedUndo() {
                refreshSpinnerAdapter();
                for (final StoredList list : DataStore.getLists()) {
                    if (oldListName.equals(list.getTitle())) {
                        switchListById(list.id);
                    }
                }
            }

        }.execute();
    }

    private void removeList() {
        // if there are no caches on this list, don't bother the user with questions.
        // there is no harm in deleting the list, he could recreate it easily
        if (CollectionUtils.isEmpty(cacheList)) {
            removeListInternal();
            return;
        }

        // ask him, if there are caches on the list
        Dialogs.confirm(this, R.string.list_dialog_remove_title, R.string.list_dialog_remove_description, R.string.list_dialog_remove, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int whichButton) {
                removeListInternal();
            }
        });
    }

    public void goMap() {
        if (!cacheToShow()) {
            return;
        }

        // apply filter settings (if there's a filter)
        final SearchResult searchToUse = getFilteredSearch();
        CGeoMap.startActivitySearch(this, searchToUse, title);
    }

    private void refreshCurrentList() {
        refreshSpinnerAdapter();
        switchListById(listId);
    }

    public static void startActivityOffline(final Context context) {
        final Intent cachesIntent = new Intent(context, CacheListActivity.class);
        Intents.putListType(cachesIntent, CacheListType.OFFLINE);
        context.startActivity(cachesIntent);
    }

    public static void startActivityOwner(final Activity context, final String userName) {
        if (!isValidUsername(context, userName)) {
            return;
        }
        final Intent cachesIntent = new Intent(context, CacheListActivity.class);
        Intents.putListType(cachesIntent, CacheListType.OWNER);
        cachesIntent.putExtra(Intents.EXTRA_USERNAME, userName);
        context.startActivity(cachesIntent);
    }

    private static boolean isValidUsername(final Activity context, final String username) {
        if (StringUtils.isBlank(username)) {
            ActivityMixin.showToast(context, R.string.warn_no_username);
            return false;
        }
        return true;
    }

    public static void startActivityFinder(final Activity context, final String userName) {
        if (!isValidUsername(context, userName)) {
            return;
        }
        final Intent cachesIntent = new Intent(context, CacheListActivity.class);
        Intents.putListType(cachesIntent, CacheListType.FINDER);
        cachesIntent.putExtra(Intents.EXTRA_USERNAME, userName);
        context.startActivity(cachesIntent);
    }

    private void prepareFilterBar() {
        if (Settings.getCacheType() != CacheType.ALL || adapter.isFiltered()) {
            final StringBuilder output = new StringBuilder(Settings.getCacheType().getL10n());

            if (adapter.isFiltered()) {
                output.append(", ").append(adapter.getFilterName());
            }

            final TextView filterTextView = ButterKnife.findById(this, R.id.filter_text);
            filterTextView.setText(output.toString());
            findViewById(R.id.filter_bar).setVisibility(View.VISIBLE);
        }
        else {
            findViewById(R.id.filter_bar).setVisibility(View.GONE);
        }
    }

    public static Intent getNearestIntent(final Activity context) {
        return Intents.putListType(new Intent(context, CacheListActivity.class), CacheListType.NEAREST);
    }

    public static Intent getHistoryIntent(final Context context) {
        return Intents.putListType(new Intent(context, CacheListActivity.class), CacheListType.HISTORY);
    }

    public static void startActivityAddress(final Context context, final Geopoint coords, final String address) {
        final Intent addressIntent = new Intent(context, CacheListActivity.class);
        Intents.putListType(addressIntent, CacheListType.ADDRESS);
        addressIntent.putExtra(Intents.EXTRA_COORDS, coords);
        addressIntent.putExtra(Intents.EXTRA_ADDRESS, address);
        context.startActivity(addressIntent);
    }

    /**
     * start list activity, by searching around the given point.
     *
     * @param name
     *            name of coordinates, will lead to a title like "Around ..." instead of directly showing the
     *            coordinates as title
     */
    public static void startActivityCoordinates(final AbstractActivity context, final Geopoint coords, @Nullable final String name) {
        if (!isValidCoords(context, coords)) {
            return;
        }
        final Intent cachesIntent = new Intent(context, CacheListActivity.class);
        Intents.putListType(cachesIntent, CacheListType.COORDINATE);
        cachesIntent.putExtra(Intents.EXTRA_COORDS, coords);
        if (StringUtils.isNotEmpty(name)) {
            cachesIntent.putExtra(Intents.EXTRA_TITLE, context.getString(R.string.around, name));
        }
        context.startActivity(cachesIntent);
    }

    private static boolean isValidCoords(final AbstractActivity context, final Geopoint coords) {
        if (coords == null) {
            context.showToast(CgeoApplication.getInstance().getString(R.string.warn_no_coordinates));
            return false;
        }
        return true;
    }

    public static void startActivityKeyword(final AbstractActivity context, final String keyword) {
        if (keyword == null) {
            context.showToast(CgeoApplication.getInstance().getString(R.string.warn_no_keyword));
            return;
        }
        final Intent cachesIntent = new Intent(context, CacheListActivity.class);
        Intents.putListType(cachesIntent, CacheListType.KEYWORD);
        cachesIntent.putExtra(Intents.EXTRA_KEYWORD, keyword);
        context.startActivity(cachesIntent);
    }

    public static void startActivityMap(final Context context, final SearchResult search) {
        final Intent cachesIntent = new Intent(context, CacheListActivity.class);
        cachesIntent.putExtra(Intents.EXTRA_SEARCH, search);
        Intents.putListType(cachesIntent, CacheListType.MAP);
        context.startActivity(cachesIntent);
    }

    public static void startActivityPocket(final AbstractActivity context, final @NonNull PocketQueryList pq) {
        final String guid = pq.getGuid();
        if (guid == null) {
            context.showToast(CgeoApplication.getInstance().getString(R.string.warn_pocket_query_select));
            return;
        }
        final Intent cachesIntent = new Intent(context, CacheListActivity.class);
        Intents.putListType(cachesIntent, CacheListType.POCKET);
        cachesIntent.putExtra(Intents.EXTRA_NAME, pq.getName());
        cachesIntent.putExtra(Intents.EXTRA_POCKET_GUID, guid);
        context.startActivity(cachesIntent);
    }

    // Loaders

    @Override
    public Loader<SearchResult> onCreateLoader(final int type, final Bundle extras) {
        if (type >= CacheListLoaderType.values().length) {
            throw new IllegalArgumentException("invalid loader type " + type);
        }
        final CacheListLoaderType enumType = CacheListLoaderType.values()[type];
        AbstractSearchLoader loader = null;
        switch (enumType) {
            case OFFLINE:
                // open either the requested or the last list
                if (extras.containsKey(Intents.EXTRA_LIST_ID)) {
                    listId = extras.getInt(Intents.EXTRA_LIST_ID);
                } else {
                    listId = Settings.getLastList();
                }
                if (listId == PseudoList.ALL_LIST.id) {
                    title = res.getString(R.string.list_all_lists);
                } else if (listId <= StoredList.TEMPORARY_LIST.id) {
                    listId = StoredList.STANDARD_LIST_ID;
                    title = res.getString(R.string.stored_caches_button);
                } else {
                    final StoredList list = DataStore.getList(listId);
                    // list.id may be different if listId was not valid
                    if (list.id != listId) {
                        showToast(getString(R.string.list_not_available));
                    }
                    listId = list.id;
                    title = list.title;
                }

                loader = new OfflineGeocacheListLoader(getBaseContext(), coords, listId);

                break;
            case HISTORY:
                title = res.getString(R.string.caches_history);
                listId = PseudoList.HISTORY_LIST.id;
                loader = new HistoryGeocacheListLoader(app, coords);
                break;
            case NEAREST:
                title = res.getString(R.string.caches_nearby);
                loader = new CoordsGeocacheListLoader(app, coords);
                break;
            case COORDINATE:
                title = coords.toString();
                loader = new CoordsGeocacheListLoader(app, coords);
                break;
            case KEYWORD:
                final String keyword = extras.getString(Intents.EXTRA_KEYWORD);
                title = listNameMemento.rememberTerm(keyword);
                loader = new KeywordGeocacheListLoader(app, keyword);
                break;
            case ADDRESS:
                final String address = extras.getString(Intents.EXTRA_ADDRESS);
                if (StringUtils.isNotBlank(address)) {
                    title = listNameMemento.rememberTerm(address);
                } else {
                    title = coords.toString();
                }
                loader = new CoordsGeocacheListLoader(app, coords);
                break;
            case FINDER:
                final String username = extras.getString(Intents.EXTRA_USERNAME);
                title = listNameMemento.rememberTerm(username);
                loader = new FinderGeocacheListLoader(app, username);
                break;
            case OWNER:
                final String ownerName = extras.getString(Intents.EXTRA_USERNAME);
                title = listNameMemento.rememberTerm(ownerName);
                loader = new OwnerGeocacheListLoader(app, ownerName);
                break;
            case MAP:
                //TODO Build Null loader
                title = res.getString(R.string.map_map);
                search = (SearchResult) extras.get(Intents.EXTRA_SEARCH);
                replaceCacheListFromSearch();
                loadCachesHandler.sendMessage(Message.obtain());
                break;
            case NEXT_PAGE:
                loader = new NextPageGeocacheListLoader(app, search);
                break;
            case POCKET:
                final String guid = extras.getString(Intents.EXTRA_POCKET_GUID);
                title = extras.getString(Intents.EXTRA_NAME);
                loader = new PocketGeocacheListLoader(app, guid);
                break;
        }
        // if there is a title given in the activity start request, use this one instead of the default
        if (extras != null && StringUtils.isNotBlank(extras.getString(Intents.EXTRA_TITLE))) {
            title = extras.getString(Intents.EXTRA_TITLE);
        }
        updateTitle();
        showProgress(true);
        showFooterLoadingCaches();

        if (loader != null) {
            loader.setRecaptchaHandler(new RecaptchaHandler(this, loader));
        }
        return loader;
    }

    @Override
    public void onLoadFinished(final Loader<SearchResult> arg0, final SearchResult searchIn) {
        // The database search was moved into the UI call intentionally. If this is done before the runOnUIThread,
        // then we have 2 sets of caches in memory. This can lead to OOM for huge cache lists.
        if (searchIn != null) {
            cacheList.clear();
            final Set<Geocache> cachesFromSearchResult = searchIn.getCachesFromSearchResult(LoadFlags.LOAD_CACHE_OR_DB);
            cacheList.addAll(cachesFromSearchResult);
            search = searchIn;
            updateAdapter();
            updateTitle();
            showFooterMoreCaches();
        }
        showProgress(false);
        hideLoading();
        invalidateOptionsMenuCompatible();
    }

    @Override
    public void onLoaderReset(final Loader<SearchResult> arg0) {
        //Not interesting
    }

    /**
     * Allow the title bar spinner to show the same subtitle like the activity itself would show.
     *
     */
    public CharSequence getCacheListSubtitle(@NonNull final AbstractList list) {
        // if this is the current list, be aware of filtering
        if (list.id == listId) {
            return getCurrentSubtitle();
        }
        // otherwise return the overall number
        final int numberOfCaches = list.getNumberOfCaches();
        if (numberOfCaches < 0) {
            return StringUtils.EMPTY;
        }
        return getCacheNumberString(getResources(), numberOfCaches);
    }

    /**
     * Calculate the subtitle of the current list depending on (optional) filters.
     *
     */
    private CharSequence getCurrentSubtitle() {
        if (search == null) {
            return getCacheNumberString(getResources(), 0);
        }
        final StringBuilder result = new StringBuilder();
        if (adapter.isFiltered()) {
            result.append(adapter.getCount()).append('/');
        }
        result.append(getCacheNumberString(getResources(), search.getCount()));
        return result.toString();
    }

    @Override
    public ShowcaseViewBuilder getShowcase() {
        if (mCacheListSpinnerAdapter != null) {
            return new ShowcaseViewBuilder(this)
                    .setTarget(new ActionViewTarget(this, Type.SPINNER))
                    .setContent(R.string.showcase_cachelist_title, R.string.showcase_cachelist_text);
        }
        return null;
    }
}
