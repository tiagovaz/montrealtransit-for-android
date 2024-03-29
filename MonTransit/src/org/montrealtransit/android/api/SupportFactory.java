package org.montrealtransit.android.api;

import org.montrealtransit.android.MyLog;

import android.os.Build;

/**
 * This class binds SDK versions with available features.
 * @author Mathieu Méa
 */
public class SupportFactory {

	/**
	 * The log tag.
	 */
	private static final String TAG = SupportFactory.class.getSimpleName();

	private static SupportUtil instance;

	/**
	 * @return the support instance
	 */
	public static SupportUtil get() {
		if (instance == null) {
			String className = SupportFactory.class.getPackage().getName();
			@SuppressWarnings("deprecation")
			// TODO use Build.VERSION.SDK_INT
			int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
			switch (sdkVersion) {
			case Build.VERSION_CODES.BASE:
			case Build.VERSION_CODES.BASE_1_1: // unsupported versions
				MyLog.d(TAG, "Unknow API Level: " + sdkVersion);
			case Build.VERSION_CODES.CUPCAKE:
				className += ".CupcakeSupport"; // 3
				break;
			case Build.VERSION_CODES.DONUT:
				className += ".DonutSupport"; // 4
				break;
			case Build.VERSION_CODES.ECLAIR:
			case Build.VERSION_CODES.ECLAIR_0_1:
			case Build.VERSION_CODES.ECLAIR_MR1:
				className += ".EclairSupport"; // 5 6 7
				break;
			case Build.VERSION_CODES.FROYO:
				className += ".FroyoSupport"; // 8
				break;
			case Build.VERSION_CODES.GINGERBREAD:
			case Build.VERSION_CODES.GINGERBREAD_MR1:
				className += ".GingerbreadSupport"; // 9 10
				break;
			case Build.VERSION_CODES.HONEYCOMB:
			case Build.VERSION_CODES.HONEYCOMB_MR1:
			case Build.VERSION_CODES.HONEYCOMB_MR2:
				className += ".HoneycombSupport"; // 11 12 13
				break;
			case Build.VERSION_CODES.ICE_CREAM_SANDWICH:
			case Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1:
				className += ".IceCreamSandwichSupport"; // 14 15
				break;
			case Build.VERSION_CODES.JELLY_BEAN:
			case Build.VERSION_CODES.JELLY_BEAN_MR1:
				className += ".JellyBeanSupport"; // 16 17
				break;
			case Build.VERSION_CODES.JELLY_BEAN_MR2:
				className += ".JellyBeanSupportMR2"; // 18
				break;
			case Build.VERSION_CODES.KITKAT:
			case Build.VERSION_CODES.KITKAT_WATCH: // not really supported
				className += ".KitKatSupport"; // 19 // 20
				break;
			case Build.VERSION_CODES.LOLLIPOP:
				className += ".LollipopSupport"; // 21
				break;
			case Build.VERSION_CODES.LOLLIPOP_MR1:
				className += ".LollipopMR1Support"; // 22
				break;
			default:
				MyLog.w(TAG, "Unknow API Level: %s", sdkVersion);
				className += ".LollipopMR1Support"; // default for newer SDK
				break;
			}

			try {
				Class<?> detectorClass = Class.forName(className);
				instance = (SupportUtil) detectorClass.getConstructor().newInstance();
			} catch (Exception e) {
				MyLog.e(TAG, e, "INTERNAL ERROR!");
				throw new RuntimeException(e);
			}
		}
		return instance;
	}
}
