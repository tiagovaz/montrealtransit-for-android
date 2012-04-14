package org.montrealtransit.android.activity;

import java.util.ArrayList;
import java.util.List;

import org.montrealtransit.android.AnalyticsUtils;
import org.montrealtransit.android.LocationUtils;
import org.montrealtransit.android.MyLog;
import org.montrealtransit.android.R;
import org.montrealtransit.android.SensorUtils;
import org.montrealtransit.android.SensorUtils.ShakeListener;
import org.montrealtransit.android.Utils;
import org.montrealtransit.android.data.ABikeStation;
import org.montrealtransit.android.data.ClosestPOI;
import org.montrealtransit.android.provider.DataManager;
import org.montrealtransit.android.provider.DataStore;
import org.montrealtransit.android.provider.BixiStore.BikeStation;
import org.montrealtransit.android.provider.DataStore.Fav;
import org.montrealtransit.android.services.ClosestBikeStationsFinderTask;
import org.montrealtransit.android.services.ClosestBikeStationsFinderTask.ClosestBikeStationsFinderListener;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Location;
import android.location.LocationListener;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Display a list of bike stations.
 * @author Mathieu Méa
 */
public class BikeTab extends Activity implements LocationListener, ClosestBikeStationsFinderListener, SensorEventListener, ShakeListener, OnItemClickListener {

	/**
	 * The log tag.
	 */
	private static final String TAG = BikeTab.class.getSimpleName();
	/**
	 * The tracker tag.
	 */
	private static final String TRACKER_TAG = "/BikeStations";

	/**
	 * Store the device location.
	 */
	private Location location;
	/**
	 * Is the location updates enabled?
	 */
	private boolean locationUpdatesEnabled = false;

	/**
	 * The closest bike stations (with distance => ordered).
	 */
	private List<ABikeStation> closestBikeStations;
	/**
	 * The location used to generate the closest bike stations list.
	 */
	private Location closestBikeStationsLocation;
	/**
	 * The location address used to generate the closest bike stations list;
	 */
	private Address closestBikeStationsLocationAddress;

	/**
	 * The bike stations list adapter.
	 */
	private ArrayAdapter<ABikeStation> adapter;

	/**
	 * The bike stations IDs ordered by distance (closest first).
	 */
	private List<String> orderedStationsIds;
	/**
	 * The task used to load the closest bike stations.
	 */
	private ClosestBikeStationsFinderTask closestBikeStationsTask;

	private static final int NB_CLOSEST_BIKE_STATIONS = ClosestBikeStationsFinderTask.NO_LIMIT;

	/**
	 * The acceleration apart from gravity.
	 */
	private float lastSensorAcceleration = 0.00f;
	/**
	 * The last acceleration including gravity.
	 */
	private float lastSensorAccelerationIncGravity = SensorManager.GRAVITY_EARTH;
	/**
	 * The last sensor update time-stamp.
	 */
	private long lastSensorUpdate = -1;
	/**
	 * True if the share was already handled (should be reset in {@link #onResume()}).
	 */
	private boolean shakeHandled;
	/**
	 * The favorites bike station terminal names.
	 */
	private List<String> favTerminalNames;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		MyLog.v(TAG, "onCreate()");
		super.onCreate(savedInstanceState);
		// set the UI
		setContentView(R.layout.bike_station_tab);

		if (Utils.isVersionOlderThan(Build.VERSION_CODES.DONUT)) {
			onCreatePreDonut();
		}

		showClosestBikeStations();
	}

	/**
	 * {@link #onCreate(Bundle)} method only for Android versions older than 1.6.
	 */
	private void onCreatePreDonut() {
		findViewById(R.id.closest_bike_stations_refresh).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				refreshOrStopRefreshClosestStations(v);
			}
		});
	}

	@Override
	protected void onResume() {
		MyLog.v(TAG, "onResume()");
		// IF location updates should be enabled DO
		if (this.locationUpdatesEnabled) {
			new AsyncTask<Void, Void, Location>() {
				@Override
				protected Location doInBackground(Void... params) {
					return LocationUtils.getBestLastKnownLocation(BikeTab.this);
				}

				@Override
				protected void onPostExecute(Location result) {
					// IF there is a valid last know location DO
					if (result != null) {
						// set the new distance
						setLocation(result);
						updateDistancesWithNewLocation();
					}
					// re-enable
					LocationUtils.enableLocationUpdates(BikeTab.this, BikeTab.this);
				};

			}.execute();
		}
		AnalyticsUtils.trackPageView(this, TRACKER_TAG);
		// refresh favorites
		refreshFavoriteTerminalNamesFromDB();
		SensorUtils.registerShakeListener(this, this);
		this.shakeHandled = false;
		super.onResume();
	}

	@Override
	public void onSensorChanged(SensorEvent se) {
		// MyLog.v(TAG, "onSensorChanged()");
		SensorUtils.checkForShake(se.values, this.lastSensorUpdate, this.lastSensorAccelerationIncGravity, this.lastSensorAcceleration, this);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// MyLog.v(TAG, "onAccuracyChanged()");
	}

	@Override
	public void onShake() {
		MyLog.v(TAG, "onShake()");
		showClosestStation();
	}

	/**
	 * Show the closest subway line station (if possible).
	 */
	private void showClosestStation() {
		MyLog.v(TAG, "showClosestStation()");
		if (!this.shakeHandled && this.closestBikeStations != null && this.closestBikeStations.size() > 0) {
			Toast.makeText(this, R.string.shake_closest_bike_station_selected, Toast.LENGTH_SHORT).show();
			// show bike station view
			Intent intent = new Intent(this, BikeStationInfo.class);
			intent.putExtra(BikeStationInfo.EXTRA_STATION_TERMINAL_NAME, this.closestBikeStations.get(0).getTerminalName());
			intent.putExtra(BikeStationInfo.EXTRA_STATION_NAME, this.closestBikeStations.get(0).getName());
			startActivity(intent);
			this.shakeHandled = true;
		}
	}

	@Override
	protected void onPause() {
		MyLog.v(TAG, "onPause()");
		LocationUtils.disableLocationUpdates(this, this);
		SensorUtils.unregisterShakeListener(this, this);
		super.onPause();
	}

	/**
	 * @param newLocation the new location
	 */
	private void setLocation(Location newLocation) {
		if (newLocation != null) {
			// MyLog.d(TAG, "new location: %s.", LocationUtils.locationToString(newLocation));
			if (this.location == null || LocationUtils.isMoreRelevant(this.location, newLocation)) {
				this.location = newLocation;
			}
		}
	}

	/**
	 * Update the bike stations distances with the new location.
	 */
	private void updateDistancesWithNewLocation() {
		MyLog.v(TAG, "updateDistancesWithNewLocation()");
		// IF no closest bike stations AND new location DO
		if (this.closestBikeStations == null && getLocation() != null) {
			// start refreshing if not running.
			refreshClosestBikeStations();
			return;
		}
		// ELSE IF there are closest stations AND new location DO
		if (this.closestBikeStations != null && getLocation() != null) {
			// update the list distances
			boolean isDetailed = UserPreferences.getPrefDefault(this, UserPreferences.PREFS_DISTANCE, UserPreferences.PREFS_DISTANCE_DEFAULT).equals(
					UserPreferences.PREFS_DISTANCE_DETAILED);
			String distanceUnit = UserPreferences.getPrefDefault(this, UserPreferences.PREFS_DISTANCE_UNIT, UserPreferences.PREFS_DISTANCE_UNIT_DEFAULT);
			float accuracyInMeters = getLocation().getAccuracy();
			for (ABikeStation station : this.closestBikeStations) {
				// distance
				station.setDistance(getLocation().distanceTo(LocationUtils.getNewLocation(station.getLat(), station.getLng())));
				station.setDistanceString(Utils.getDistanceString(station.getDistance(), accuracyInMeters, isDetailed, distanceUnit));
			}
			generateOrderedStationsIds();
			// update the view
			if (this.adapter != null) {
				this.adapter.notifyDataSetChanged();
			}
		}
	}

	/**
	 * Generate the ordered subway line station IDs.
	 */
	public void generateOrderedStationsIds() {
		MyLog.v(TAG, "generateOrderedStationsIds()");
		this.orderedStationsIds = new ArrayList<String>();
		for (ABikeStation orderedStation : this.closestBikeStations) {
			this.orderedStationsIds.add(orderedStation.getTerminalName());
		}
	}

	/**
	 * Set the closest stations as not loading.
	 */
	private void setClosestStationsNotLoading() {
		MyLog.v(TAG, "setClosestStationsNotLoading()");
		View closestStationsTitle = findViewById(R.id.closest_bike_stations_title);
		// show refresh icon instead of loading
		closestStationsTitle.findViewById(R.id.closest_bike_stations_refresh).setVisibility(View.VISIBLE);
		// hide progress bar
		closestStationsTitle.findViewById(R.id.progress_bar_closest).setVisibility(View.INVISIBLE);
	}

	/**
	 * Show the new closest bike stations.
	 */
	private void showNewClosestBikeStations() {
		MyLog.v(TAG, "showNewClosestBikeStations()");
		if (this.closestBikeStations != null) {
			// set the closest station title
			showNewClosestBikeStationsTitle();
			// hide loading
			findViewById(R.id.closest_bike_stations_list_loading).setVisibility(View.GONE); // hide
			// show stations list
			ListView list = (ListView) findViewById(R.id.closest_bike_stations_list);
			list.setVisibility(View.VISIBLE);
			ABikeStation[] array = this.closestBikeStations.toArray(new ABikeStation[] {});
			this.adapter = new ArrayAdapterWithCustomView(this, R.layout.bike_station_tab_closest_stations_list_item, array);
			list.setAdapter(this.adapter);
			list.setOnItemClickListener(this);

			setClosestStationsNotLoading();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		MyLog.v(TAG, "onItemClick(%s, %s,%s,%s)", parent.getId(), view.getId(), position, id);
		if (this.closestBikeStations != null && position < this.closestBikeStations.size() && this.closestBikeStations.get(position) != null) {
			Intent intent = new Intent(this, BikeStationInfo.class);
			BikeStation selectedStation = this.closestBikeStations.get(position);
			intent.putExtra(BikeStationInfo.EXTRA_STATION_TERMINAL_NAME, selectedStation.getTerminalName());
			intent.putExtra(BikeStationInfo.EXTRA_STATION_NAME, selectedStation.getName());
			startActivity(intent);
		}
	}

	/**
	 * set the closest station title
	 */
	public void showNewClosestBikeStationsTitle() {
		if (this.closestBikeStationsLocationAddress != null && this.closestBikeStationsLocation != null) {
			((TextView) findViewById(R.id.closest_bike_stations_title).findViewById(R.id.closest_bike_stations_title_text)).setText(LocationUtils
					.getLocationString(this, this.closestBikeStationsLocationAddress, this.closestBikeStationsLocation.getAccuracy()));
		}
	}

	/**
	 * A custom array adapter with custom {@link ArrayAdapterWithCustomView#getView(int, View, ViewGroup)}
	 */
	private class ArrayAdapterWithCustomView extends ArrayAdapter<ABikeStation> {

		/**
		 * The layout inflater.
		 */
		private LayoutInflater layoutInflater;
		/**
		 * The bike stations.
		 */
		private ABikeStation[] bikeStations;
		/**
		 * The view ID.
		 */
		private int viewId;

		/**
		 * The default constructor.
		 * @param context the context
		 * @param viewId the the view ID
		 * @param objects the stations
		 */
		public ArrayAdapterWithCustomView(Context context, int viewId, ABikeStation[] bikeStations) {
			super(context, viewId, bikeStations);
			this.viewId = viewId;
			this.bikeStations = bikeStations;
			this.layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			// MyLog.v(TAG, "getView(%s)", position);
			if (convertView == null) {
				convertView = this.layoutInflater.inflate(this.viewId, parent, false);
			}

			ABikeStation bikeStation = this.bikeStations[position];
			if (bikeStation != null) {
				// bike station name
				((TextView) convertView.findViewById(R.id.station_name)).setText(Utils.cleanBikeStationName(bikeStation.getName()));
				// favorite
				if (BikeTab.this.favTerminalNames != null && BikeTab.this.favTerminalNames.contains(bikeStation.getTerminalName())) {
					convertView.findViewById(R.id.fav_img).setVisibility(View.VISIBLE);
				} else {
					convertView.findViewById(R.id.fav_img).setVisibility(View.GONE);
				}
				// status (not installed, locked..)
				if (!bikeStation.isInstalled() || bikeStation.isLocked()) {
					((TextView) convertView.findViewById(R.id.station_name)).setTextColor(Utils.getTextColorSecondary(getContext()));
				} else {
					((TextView) convertView.findViewById(R.id.station_name)).setTextColor(Utils.getTextColorPrimary(getContext()));
				}
				// distance
				if (!TextUtils.isEmpty(bikeStation.getDistanceString())) {
					((TextView) convertView.findViewById(R.id.distance)).setText(bikeStation.getDistanceString());
					convertView.findViewById(R.id.distance).setVisibility(View.VISIBLE);
				} else {
					convertView.findViewById(R.id.distance).setVisibility(View.GONE);
					((TextView) convertView.findViewById(R.id.distance)).setText(null);
				}
				// closest bike station
				int index = -1;
				if (BikeTab.this.orderedStationsIds != null) {
					index = BikeTab.this.orderedStationsIds.indexOf(bikeStation.getTerminalName());
				}
				switch (index) {
				case 0:
					((TextView) convertView.findViewById(R.id.station_name)).setTypeface(Typeface.DEFAULT_BOLD);
					((TextView) convertView.findViewById(R.id.distance)).setTypeface(Typeface.DEFAULT_BOLD);
					((TextView) convertView.findViewById(R.id.distance)).setTextColor(Utils.getTextColorPrimary(getContext()));
					break;
				default:
					((TextView) convertView.findViewById(R.id.station_name)).setTypeface(Typeface.DEFAULT);
					((TextView) convertView.findViewById(R.id.distance)).setTypeface(Typeface.DEFAULT);
					((TextView) convertView.findViewById(R.id.distance)).setTextColor(Utils.getTextColorSecondary(getContext()));
					break;
				}
			}
			return convertView;
		}
	}

	/**
	 * Set the closest stations as error.
	 */
	private void setClosestStationsError(String errorMessage) {
		MyLog.v(TAG, "setClosestStationsError(%s)", errorMessage);
		// IF there are already stations DO
		if (this.closestBikeStations != null) {
			// notify the user but keep showing the old stations
			Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
		} else {
			// show the error message
			View loadingLayout = findViewById(R.id.closest_bike_stations_list_loading);
			TextView mainMsgTv = (TextView) loadingLayout.findViewById(R.id.main_msg);
			mainMsgTv.setText(errorMessage);
			mainMsgTv.setVisibility(View.VISIBLE);
			((TextView) loadingLayout.findViewById(R.id.detail_msg)).setVisibility(View.GONE);
		}
		setClosestStationsNotLoading();
	}

	/**
	 * Set the closest stations as loading.
	 */
	private void setClosestStationsLoading(String detailMsg) {
		MyLog.v(TAG, "setClosestStationsLoading(%s)", detailMsg);
		View closestStationsTitle = findViewById(R.id.closest_bike_stations_title);
		if (this.closestBikeStations == null) {
			// set the loading message
			// remove last location from the list divider
			((TextView) closestStationsTitle.findViewById(R.id.closest_bike_stations_title_text)).setText(R.string.closest_bike_stations);
			if (findViewById(R.id.closest_bike_stations_list) != null) { // IF inflated/present DO
				// hide the list
				findViewById(R.id.closest_bike_stations_list).setVisibility(View.GONE);
			}
			// show loading
			findViewById(R.id.closest_bike_stations_list_loading).setVisibility(View.VISIBLE);
			TextView detailMsgTv = (TextView) findViewById(R.id.closest_bike_stations_list_loading).findViewById(R.id.detail_msg);
			detailMsgTv.setVisibility(View.VISIBLE);
			if (detailMsg == null) { // show waiting for location
				detailMsg = getString(R.string.waiting_for_location_fix);
			}
			detailMsgTv.setText(detailMsg);
			// } else { just notify the user ?
		}
		// show stop icon instead of refresh
		closestStationsTitle.findViewById(R.id.closest_bike_stations_refresh).setVisibility(View.INVISIBLE);
		// show progress bar
		closestStationsTitle.findViewById(R.id.progress_bar_closest).setVisibility(View.VISIBLE);
	}

	/**
	 * Show the closest stations UI.
	 */
	public void showClosestBikeStations() {
		MyLog.v(TAG, "showClosestBikeStations()");
		// enable location updates
		// IF location updates are not already enabled DO
		if (!this.locationUpdatesEnabled) {
			// enable
			LocationUtils.enableLocationUpdates(this, this);
			this.locationUpdatesEnabled = true;
		}
		// IF there is no closest bike stations DO
		if (this.closestBikeStations == null) {
			// generate the closest bike stations list
			refreshClosestBikeStations();
		} else {
			// show the closest stations
			showNewClosestBikeStations();
			// IF the latest location is too old DO
			if (LocationUtils.isTooOld(this.closestBikeStationsLocation)) {
				// start refreshing
				refreshClosestBikeStations();
			}
		}
	}

	/**
	 * Refresh the closest stations if not running.
	 * @param v a view (not used)
	 */
	public void refreshOrStopRefreshClosestStations(View v) {
		MyLog.v(TAG, "refreshOrStopRefreshClosestStations()");
		refreshClosestBikeStations();
	}

	/**
	 * Refresh the closest bike stations list.
	 */
	private void refreshClosestBikeStations() {
		MyLog.v(TAG, "refreshClosestStations()");
		// IF the task is NOT already running DO
		if (this.closestBikeStationsTask == null || !this.closestBikeStationsTask.getStatus().equals(AsyncTask.Status.RUNNING)) {
			setClosestStationsLoading(null);
			// IF location found DO
			Location locationUsed = getLocation();
			if (locationUsed != null) {
				// find the closest stations
				this.closestBikeStationsTask = new ClosestBikeStationsFinderTask(this, this, NB_CLOSEST_BIKE_STATIONS);
				this.closestBikeStationsTask.execute(locationUsed);
				this.closestBikeStationsLocation = locationUsed;
				new AsyncTask<Location, Void, Address>() {

					@Override
					protected Address doInBackground(Location... locations) {
						return LocationUtils.getLocationAddress(BikeTab.this, locations[0]);
					}

					@Override
					protected void onPostExecute(Address result) {
						boolean refreshRequired = BikeTab.this.closestBikeStationsLocationAddress == null;
						BikeTab.this.closestBikeStationsLocationAddress = result;
						if (refreshRequired) {
							showNewClosestBikeStationsTitle();
						}
					}

				}.execute(this.closestBikeStationsLocation);
			}
			// ELSE wait for location...
		}
	}

	@Override
	public void onClosestBikeStationsProgress(String progress) {
		MyLog.v(TAG, "onClosestBikeStationsProgress(%s)", progress);
		setClosestStationsLoading(progress);
	}

	@Override
	public void onClosestBikeStationsDone(ClosestPOI<ABikeStation> result) {
		// MyLog.v(TAG, "onClosestBikeStationsDone(%s)", Utils.getCollectionSize(result.getPoiListOrNull()));
		if (result == null || result.getPoiListOrNull() == null) {
			// show the error
			setClosestStationsError(result == null ? null : result.getErrorMessage());
		} else {
			// get the result
			this.closestBikeStations = result.getPoiList();
			generateOrderedStationsIds();
			refreshFavoriteTerminalNamesFromDB();
			// shot the result
			showNewClosestBikeStations();
			setClosestStationsNotLoading();
		}
	}

	/**
	 * Find favorites bike stations terminal names.
	 */
	private void refreshFavoriteTerminalNamesFromDB() {
		new AsyncTask<Void, Void, List<Fav>>() {
			@Override
			protected List<Fav> doInBackground(Void... params) {
				return DataManager.findFavsByTypeList(getContentResolver(), DataStore.Fav.KEY_TYPE_VALUE_BIKE_STATIONS);
			}

			@Override
			protected void onPostExecute(List<Fav> result) {
				boolean newFav = false; // don't trigger update if favorites are the same
				if (Utils.getCollectionSize(result) != Utils.getCollectionSize(BikeTab.this.favTerminalNames)) {
					newFav = true; // different size => different favorites
				}
				List<String> newfavTerminalNames = new ArrayList<String>();
				for (Fav busStopFav : result) {
					if (BikeTab.this.favTerminalNames == null || !BikeTab.this.favTerminalNames.contains(busStopFav.getFkId())) {
						newfavTerminalNames.add(busStopFav.getFkId()); // store terminal name
						newFav = true; // new favorite
					} else {
						newfavTerminalNames.add(busStopFav.getFkId()); // store terminal name
					}
				}
				BikeTab.this.favTerminalNames = newfavTerminalNames;
				// trigger change if necessary
				if (newFav && BikeTab.this.adapter != null) {
					BikeTab.this.adapter.notifyDataSetChanged();
				}
			};
		}.execute();
	}

	/**
	 * @return the location
	 */
	public Location getLocation() {
		if (this.location == null) {
			new AsyncTask<Void, Void, Location>() {
				@Override
				protected Location doInBackground(Void... params) {
					return LocationUtils.getBestLastKnownLocation(BikeTab.this);
				}

				@Override
				protected void onPostExecute(Location result) {
					if (result != null) {
						BikeTab.this.setLocation(result);
					}
					// enable location updates if necessary
					if (!BikeTab.this.locationUpdatesEnabled) {
						LocationUtils.enableLocationUpdates(BikeTab.this, BikeTab.this);
						BikeTab.this.locationUpdatesEnabled = true;
					}
				}

			}.execute();
		}
		return this.location;
	}

	@Override
	public void onLocationChanged(Location location) {
		MyLog.v(TAG, "onLocationChanged()");
		this.setLocation(location);
		updateDistancesWithNewLocation();
	}

	@Override
	public void onProviderEnabled(String provider) {
		MyLog.v(TAG, "onProviderEnabled(%s)", provider);
	}

	@Override
	public void onProviderDisabled(String provider) {
		MyLog.v(TAG, "onProviderDisabled(%s)", provider);
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		MyLog.v(TAG, "onStatusChanged(%s, %s)", provider, status);
	}

	@Override
	protected void onDestroy() {
		MyLog.v(TAG, "onDestroy()");
		if (this.closestBikeStationsTask != null) {
			this.closestBikeStationsTask.cancel(true);
			this.closestBikeStations = null;
		}
		super.onDestroy();
	}
}
