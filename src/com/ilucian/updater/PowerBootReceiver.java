package com.ilucian.updater;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

/**
 * 
 * @author Maksym Fedyay
 *
 */
public class PowerBootReceiver extends WakefulBroadcastReceiver{

	public static final String TAG = PowerBootReceiver.class.getName();
	
	@Override
	public void onReceive(Context context, Intent intent) {
		
		if(isServiceRunning(UpdaterService.class, context)){
			Log.i(TAG, UpdaterService.class.getName() + " already running");
		} else {
			Intent intent1 = new Intent(context, UpdaterService.class);
			startWakefulService(context, intent1);
		}
		
	}
	
	/**
	 * Checks if a particular {@link Service} is already running
	 * @param serviceClass - {@link Class} of service to be cheched
	 * @param context
	 * @return <code>true</code> if service already running <code>false</code> otherwise
	 */
	private boolean isServiceRunning(Class<?> serviceClass, Context context) {
	    ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if (serviceClass.getName().equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}
	
}
