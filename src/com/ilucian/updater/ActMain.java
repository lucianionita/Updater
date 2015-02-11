package com.ilucian.updater;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.widget.TextView;
import android.widget.Toast;

public class ActMain extends Activity {
	
	
	/*
	 * Since version Android 3.1+ you don't recieve 
	 * BOOT_COMPLETE if user never started yor app at 
	 * least once or user "force closed" application. 
	 * This was done to prevent malware automaticaly 
	 * register service. This security hole was closed 
	 * in newer versions of Android.
	 */
	
//	private TextView tvExternalDiskSize;
//	private TextView tvInternallDiskSize;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_main);
		Toast.makeText(this, R.string.toast_launch, Toast.LENGTH_LONG).show();
		
//		this.tvExternalDiskSize = (TextView) findViewById(R.id.external_disk_size);
//		this.tvInternallDiskSize = (TextView) findViewById(R.id.internal_disk_size);
//		
//		long externalSize = UpdaterService.countAvailableSpace(Environment.getExternalStorageDirectory());
//		long internallSize = UpdaterService.countAvailableSpace(Environment.getDataDirectory());
//		
//		this.tvExternalDiskSize.setText(String.valueOf(externalSize));
//		this.tvInternallDiskSize.setText(String.valueOf(internallSize));
		
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		finish();
	}
	
} 
