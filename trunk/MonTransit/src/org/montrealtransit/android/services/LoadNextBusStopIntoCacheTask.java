package org.montrealtransit.android.services;

import java.util.Map;

import org.montrealtransit.android.BusUtils;
import org.montrealtransit.android.MyLog;
import org.montrealtransit.android.PrefetchingUtils;
import org.montrealtransit.android.Utils;
import org.montrealtransit.android.data.RouteTripStop;
import org.montrealtransit.android.data.StopTimes;
import org.montrealtransit.android.provider.DataManager;
import org.montrealtransit.android.provider.DataStore.Cache;
import org.montrealtransit.android.services.nextstop.IStmInfoTask;
import org.montrealtransit.android.services.nextstop.NextStopListener;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Process;

/**
 * Load next bus stops into cache in not in cache.
 */
public class LoadNextBusStopIntoCacheTask extends AsyncTask<Void, Void, Void> implements NextStopListener {

	public static final String TAG = LoadNextBusStopIntoCacheTask.class.getSimpleName();

	private Context context;

	private RouteTripStop routeTripStop;

	private NextStopListener from;

	private boolean prefetch;

	private boolean force;

	private IStmInfoTask wwwTask;

	public LoadNextBusStopIntoCacheTask(Context context, RouteTripStop routeTripStop, NextStopListener from, boolean prefetch, boolean force) {
		MyLog.d(TAG, "LoadNextBusStopIntoCacheTask()");
		this.context = context;
		this.routeTripStop = routeTripStop;
		this.from = from;
		this.prefetch = prefetch;
		this.force = force;
	}

	@Override
	protected Void doInBackground(Void... params) {
		MyLog.d(TAG, "doInBackground()");
		if (prefetch) {
			Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST);
			if (!PrefetchingUtils.isPrefetching(context)) {
				MyLog.d(TAG, "prefetching disabled");
				return null; // disabled
			}
			if (!PrefetchingUtils.isConnectedToWifi(context) && PrefetchingUtils.isPrefetchingWiFiOnly(context)) {
				MyLog.d(TAG, "prefetching over Wifi only");
				return null; // over WiFi only
			}
		}
		if (!force && recentDataAlreadyInCache()) {
			MyLog.d(TAG, "bus stop already in cache (%s)", this.routeTripStop.getUUID());
			return null;
		}
		MyLog.d(TAG, "Loading bus stop %s data...", this.routeTripStop.getUUID());
		this.wwwTask = new IStmInfoTask(this.context, this, this.routeTripStop);
		this.wwwTask.execute();
		return null;
	}

	private boolean recentDataAlreadyInCache() {
		// load cache from database
		Cache cache = DataManager.findCache(context.getContentResolver(), Cache.KEY_TYPE_VALUE_AUTHORITY_ROUTE_TRIP_STOP, routeTripStop.getUUID());
		// compute the too old date
		int tooOld = Utils.currentTimeSec() - BusUtils.CACHE_NOT_USEFUL_IN_SEC;
		// IF the cache is too old DO
		if (cache != null && tooOld >= cache.getDate()) {
			// don't use the cache
			cache = null;
			// delete all too old cache
			try {
				DataManager.deleteCacheOlderThan(context.getContentResolver(), tooOld);
			} catch (Exception e) {
				MyLog.w(TAG, e, "Can't clean the cache!");
			}
		}
		return cache != null;
	}

	@Override
	public void onNextStopsProgress(String progress) {
		MyLog.v(TAG, "onNextStopsProgress(%s)", progress);
		if (this.from != null) {
			this.from.onNextStopsProgress(progress);
		}
	}

	@Override
	public void onNextStopsLoaded(final Map<String, StopTimes> results) {
		MyLog.v(TAG, "onNextStopsLoaded()");
		// if (results == null || results.size() <= 0) {
		// MyLog.d(TAG, "onNextStopsLoaded() > no result!");
		// return; // no result
		// }
		if (this.from != null) {
			this.from.onNextStopsLoaded(results);
		} else {
			MyLog.d(TAG, "onNextStopsLoaded() > no listener!");
		}
		// if (containResult) {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				// MyLog.d(TAG, "onNextStopsLoaded() > %s results to store in cache.", results.size());
				// store in cache
				if (results != null) {
					for (final String uuid : results.keySet()) {
						final StopTimes stopTimes = results.get(uuid);
						saveToCache(LoadNextBusStopIntoCacheTask.this.context, uuid, stopTimes);
					}
				}
				return null;
			}

		}.execute();
	}

	public static void saveToCache(Context context, String uuid, StopTimes stopTimes) {
		MyLog.v(TAG, "saveToCache(%s,%s)", uuid, stopTimes);
		if (stopTimes == null || stopTimes.getSTimes().size() == 0) {
			return;
		}
		Cache newCache = new Cache(Cache.KEY_TYPE_VALUE_AUTHORITY_ROUTE_TRIP_STOP, uuid, stopTimes.serialized());
		// remove existing cache for this bus stop
		DataManager.deleteCacheIfExist(context.getContentResolver(), Cache.KEY_TYPE_VALUE_AUTHORITY_ROUTE_TRIP_STOP, uuid);
		// save the new value to cache
		DataManager.addCache(context.getContentResolver(), newCache);
	}

}