package org.montrealtransit.android.activity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.montrealtransit.android.MyLog;
import org.montrealtransit.android.R;
import org.montrealtransit.android.Utils;
import org.montrealtransit.android.provider.StmManager;
import org.montrealtransit.android.provider.StmStore;
import org.montrealtransit.android.provider.StmStore.BusLine;
import org.montrealtransit.android.provider.StmStore.SubwayLine;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.ExpandableListView.OnChildClickListener;

/**
 * Show information about a subway station.
 * @author Mathieu M�a
 */
public class SubwayStationInfo extends Activity implements /* ViewBinder, */OnChildClickListener {

	/**
	 * Extra for the subway station ID.
	 */
	public static final String EXTRA_STATION_ID = "station_id";
	/**
	 * The log tag.
	 */
	private static final String TAG = SubwayStationInfo.class.getSimpleName();
	/**
	 * The subway station.
	 */
	private StmStore.SubwayStation subwayStation;
	/**
	 * The subway lines.
	 */
	private List<SubwayLine> subwayLines;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		MyLog.v(TAG, "onCreate()");
		super.onCreate(savedInstanceState);
		// set the UI
		setContentView(R.layout.subway_station_info);
		((ExpandableListView) findViewById(R.id.bus_line_list)).setEmptyView(findViewById(R.id.empty_bus_line_list));
		((ExpandableListView) findViewById(R.id.bus_line_list)).setOnChildClickListener(this);

		showNewSubwayStation(Utils.getSavedStringValue(this.getIntent(), savedInstanceState, SubwayStationInfo.EXTRA_STATION_ID));
	}

	/**
	 * Show a new subway station
	 * @param newStationId the new subway station ID
	 */
	private void showNewSubwayStation(String newStationId) {
		MyLog.v(TAG, "showNewSubwayStation(" + newStationId + ")");
		if (this.subwayStation == null || !this.subwayStation.getId().equals(newStationId)) {
			MyLog.v(TAG, "display a new subway station");
			this.subwayStation = StmManager.findSubwayStation(getContentResolver(), newStationId);
			this.subwayLines = StmManager.findSubwayStationLinesList(getContentResolver(), this.subwayStation.getId());
			refreshAll();
		}
	}

	/**
	 * Refresh all the UI based on the subway station.
	 */
	private void refreshAll() {
		refreshSubwayStationInfo();
		// TODO set bus line direction info
		refreshBusLines();
	}

	/**
	 * Refresh the subway station info.
	 */
	private void refreshSubwayStationInfo() {
		MyLog.v(TAG, "refreshSubwayStationInfo()");
		((TextView) findViewById(R.id.station_name)).setText(this.subwayStation.getName());
		if (this.subwayLines.size() > 0) {
			((TextView) findViewById(R.id.subway_line_string)).setText(getResources().getString(R.string.subway_line));
			((ImageView) findViewById(R.id.subway_line_1)).setVisibility(View.VISIBLE);
			((ImageView) findViewById(R.id.subway_line_1)).setImageResource(Utils.getSubwayLineImg(this.subwayLines.get(0).getNumber()));
			if (this.subwayLines.size() > 1) {
				((TextView) findViewById(R.id.subway_line_string)).setText(getResources().getString(R.string.subway_lines));
				((ImageView) findViewById(R.id.subway_line_2)).setVisibility(View.VISIBLE);
				((ImageView) findViewById(R.id.subway_line_2)).setImageResource(Utils.getSubwayLineImg(this.subwayLines.get(1).getNumber()));
				if (this.subwayLines.size() > 2) {
					((ImageView) findViewById(R.id.subway_line_3)).setVisibility(View.VISIBLE);
					((ImageView) findViewById(R.id.subway_line_3)).setImageResource(Utils.getSubwayLineImg(this.subwayLines.get(2).getNumber()));
				}
			}
		}
		// TODO bus line colors ?
	}

	/**
	 * Refresh the bus lines.
	 */
	private void refreshBusLines() {
		MyLog.v(TAG, "refreshBusLines()");
		((ExpandableListView) findViewById(R.id.bus_line_list)).setAdapter(getBusStopsEAdapter());
	}

	/**
	 * The current bus stops (group by bus line).
	 */
	private List<List<Map<String, String>>> currentChildData;
	/**
	 * The current bus lines.
	 */
	private List<Map<String, StmStore.BusLine>> currentGroupData;
	/**
	 * The bus line string.
	 */
	private static final String BUS_LINE = "line";
	/**
	 * The bus line number string.
	 */
	private static final String BUS_LINE_NUMBER = "lineNumber";

	/**
	 * @return the bus stops adapter.
	 */
	private ExpandableListAdapter getBusStopsEAdapter() {
		MyLog.v(TAG, "getBusStopsEAdapter()");
		List<StmStore.BusStop> busStopList = StmManager.findSubwayStationBusStopsExtendedList(getContentResolver(), this.subwayStation.getId());

		List<Map<String, String>> groupData = new ArrayList<Map<String, String>>();
		this.currentGroupData = new ArrayList<Map<String, StmStore.BusLine>>();
		this.currentChildData = new ArrayList<List<Map<String, String>>>();

		String currentLine = null;
		List<Map<String, String>> currrentChildren = null;
		for (StmStore.BusStop busStop : busStopList) {
			// IF this is a bus stop of a new bus line DO
			if (!busStop.getLineNumber().equals(currentLine)) {
				// create a new group for this bus line
				Map<String, StmStore.BusLine> curGroupBusLineMap = new HashMap<String, StmStore.BusLine>();
				Map<String, String> curGroupMap = new HashMap<String, String>();
				currentLine = busStop.getLineNumber();
				BusLine busLine = new BusLine();
				busLine.setNumber(busStop.getLineNumber());
				busLine.setName(busStop.getLineNameOrNull());
				busLine.setHours(busStop.getLineHoursOrNull());
				busLine.setType(busStop.getLineTypeOrNull());
				curGroupBusLineMap.put(BUS_LINE, busLine);
				curGroupMap.put(BUS_LINE_NUMBER, busLine.getNumber());
				this.currentGroupData.add(curGroupBusLineMap);
				groupData.add(curGroupMap);
				// create the children list
				currrentChildren = new ArrayList<Map<String, String>>();
				this.currentChildData.add(currrentChildren);

			}
			Map<String, String> curChildMap = new HashMap<String, String>();
			curChildMap.put(StmStore.BusStop.STOP_CODE, busStop.getCode());
			curChildMap.put(StmStore.BusStop.STOP_DIRECTION_ID, busStop.getDirectionId());
			curChildMap.put(StmStore.BusStop.STOP_LINE_NUMBER, busStop.getLineNumber());
			curChildMap.put(StmStore.BusStop.STOP_PLACE, busStop.getPlace());
			curChildMap.put(StmStore.BusStop.STOP_SUBWAY_STATION_ID, busStop.getSubwayStationId());
			curChildMap.put(StmStore.BusStop.LINE_NAME, busStop.getLineNameOrNull());
			currrentChildren.add(curChildMap);
		}

		String[] groupFrom = new String[] { BUS_LINE_NUMBER, BUS_LINE_NUMBER, BUS_LINE_NUMBER };
		int[] groupTo = new int[] { R.id.line_number, R.id.line_name, R.id.line_type };
		String[] childFrom = new String[] { StmStore.BusStop.STOP_CODE, StmStore.BusStop.STOP_PLACE, StmStore.BusStop.STOP_DIRECTION_ID };
		int[] childTo = new int[] { R.id.stop_code, R.id.label, R.id.direction_main };

		MySimpleExpandableListAdapter mAdapter = new MySimpleExpandableListAdapter(this, groupData, R.layout.subway_station_info_bus_stop_list_group_item,
		        groupFrom, groupTo, this.currentChildData, R.layout.subway_station_info_bus_stop_list_item, childFrom, childTo);
		return mAdapter;
	}

	/**
	 * Simple expandable list adapter to customize the expandable list view for bus line and bus stops.
	 * @author Mathieu M�a
	 */
	private class MySimpleExpandableListAdapter extends SimpleExpandableListAdapter {

		/**
		 * Default constructor @see SimpleExpandableListAdapter
		 */
		public MySimpleExpandableListAdapter(SubwayStationInfo subwayStationInfo, List<Map<String, String>> groupData, int simpleExpandableListItem1,
		        String[] strings, int[] is, List<List<Map<String, String>>> childData, int busLineListItem, String[] childFrom, int[] childTo) {
			super(subwayStationInfo, groupData, simpleExpandableListItem1, strings, is, childData, busLineListItem, childFrom, childTo);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
			MyLog.v(TAG, "getChildView(" + groupPosition + "," + childPosition + "," + isLastChild + ")");
			View v;
			if (convertView == null) {
				v = newChildView(isLastChild, parent);
			} else {
				v = convertView;
			}
			bindView(v, currentChildData.get(groupPosition).get(childPosition));
			return v;
		}

		/**
		 * Bind the child view.
		 * @param view the child view
		 * @param data the child data
		 */
		private void bindView(View view, Map<String, String> data) {
			((TextView) view.findViewById(R.id.stop_code)).setText(data.get(StmStore.BusStop.STOP_CODE));
			((TextView) view.findViewById(R.id.label)).setText(Utils.cleanBusStopPlace(data.get(StmStore.BusStop.STOP_PLACE)));
			((TextView) view.findViewById(R.id.direction_main)).setText(getResources().getString(
			        Utils.getBusLineDirectionStringIdFromId(data.get(StmStore.BusStop.STOP_DIRECTION_ID)).get(0)));
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
			View v;
			if (convertView == null) {
				v = newGroupView(isExpanded, parent);
			} else {
				v = convertView;
			}
			StmStore.BusLine busLine = currentGroupData.get(groupPosition).get(BUS_LINE);
			((TextView) v.findViewById(R.id.line_number)).setText(busLine.getNumber());
			((TextView) v.findViewById(R.id.line_name)).setText(busLine.getName());
			((ImageView) v.findViewById(R.id.line_type)).setImageResource(Utils.getBusLineTypeImgFromType(busLine.getType()));
			return v;
		}
	}

	/**
	 * The activity view bus stop info.
	 */
	private static final int ACTIVITY_VIEW_BUS_STOP = 1;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
		MyLog.v(TAG, "onChildClick(" + parent.getId() + "," + v.getId() + "," + groupPosition + "," + childPosition + "," + id + ")");
		String busLineNumber = this.currentChildData.get(groupPosition).get(childPosition).get(StmStore.BusStop.STOP_LINE_NUMBER);
		String busStopCode = this.currentChildData.get(groupPosition).get(childPosition).get(StmStore.BusStop.STOP_CODE);
		if (busStopCode != null && busStopCode.length() > 0) {
			Intent i = new Intent(this, BusStopInfo.class);
			i.putExtra(BusStopInfo.EXTRA_STOP_LINE_NUMBER, busLineNumber);
			i.putExtra(BusStopInfo.EXTRA_STOP_CODE, busStopCode);
			startActivityForResult(i, ACTIVITY_VIEW_BUS_STOP);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Menu for showing the subway station in Maps.
	 */
	private static final int MENU_SHOW_SUBWAY_STATION_IN_MAPS = 1;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_SHOW_SUBWAY_STATION_IN_MAPS, 0, R.string.show_in_map).setIcon(android.R.drawable.ic_menu_mapmode);
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_SHOW_SUBWAY_STATION_IN_MAPS:
			try {
				// + "?q=Station " + this.subwayStation.getName()
				Uri uri = Uri.parse("geo:" + this.subwayStation.getLat() + "," + this.subwayStation.getLng());
				startActivity(new Intent(android.content.Intent.ACTION_VIEW, uri));
				return true;
			} catch (Exception e) {
				MyLog.e(TAG, "Error while launching map", e);
				return false;
			}
		default:
			MyLog.d(TAG, "Unknow menu id: " + item.getItemId() + ".");
			return false;
		}
	}
}
