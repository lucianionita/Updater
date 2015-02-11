package com.ilucian.updater;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SyncFailedException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.math.BigInteger;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectHandler;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.StatFs;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.util.Log;

/**
 * 
 * @author Maksym Fedyay
 *
 */
public class UpdaterService extends Service implements Runnable{
	
	/**
	 * Tag for logging 
	 */
	public static final String TAG = UpdaterService.class.getName();
	
	/**
	 * Url of source where stored the most recent application verionCode
	 */
	private static final String URL_VERSION_CODE = "http://52.0.157.162:8989/version.txt";
	/**
	 * Url of source where resides APK itself 
	 */
	private static final String URL_APK = "http://52.0.157.162:8989/version.txt";
	/**
	 * Path where APK will be saved
	 */
	private static final String APK_DESIGNATION = "/videocam/";
	/**
	 * APK file name 
	 */
	private static final String APK_FILE_NAME = "DummyApp.apk";
	/**
	 * Name of the package we want to update
	 */
	private static final String PACKAGE_NAME = "DummyApp";
	/**
	 * Amount of time units for period between network lookups
	 */
	private static final int CHECK_PERIOD = 60;					// time units currently SECONDs
	
//	/**
//	 * 
//	 */
//	private static final String PACKAGE_TO_UPDATE = "";
	
	
	private Handler handler;
	private ScheduledExecutorService executor;
	private PendingIntent pendingIntent;
	
	/*
	 * 
	 */
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	/*
	 * 
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		if(intent == null){
			Intent intent_ = new Intent(getApplication(), UpdaterService.class);
			pendingIntent = PendingIntent.getService(getBaseContext(), 0, intent_, intent_.getFlags());
		} else{
			pendingIntent = PendingIntent.getService(getBaseContext(), 0, new Intent(intent), intent.getFlags());
		}
		
		// Restart service automatically if 
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread thread, Throwable ex) {
				Log.e(TAG, "===>UNCAUGHTEXCEPTION CAUGHT");
				// TODO: request restart service here
				AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
				mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 3000, pendingIntent);
				System.exit(2);
			}
		});
		
		Log.i(TAG, "Updater service STARTED");

		this.executor = Executors.newScheduledThreadPool(1);
		this.executor.scheduleAtFixedRate(this, CHECK_PERIOD, CHECK_PERIOD, TimeUnit.SECONDS);
			
		this.handler = new Handler(getMainLooper());
		
//		return super.onStartCommand(intent, flags, startId);
		return Service.START_STICKY; 
	}
	
	/**
	 * Remote version code of APK file
	 */
	private int remoteVersion;
	
	/**
	 * MD5 of loaded APK file
	 */
	private String apkMD5;
	
	/**
	 * Loaded APK file
	 */
	private File file;
	
	/*
	 * Runnable implementation
	 */
	@Override
	public void run() {
		String remoteVersionCode = loadRemoteVersionCode();
		
		if(remoteVersionCode == null) return;
		
		int remoteVersion = Integer.valueOf(remoteVersionCode);
		
		// Use real package name instead
		// XXX: stubbed package name
		int localVersion = getPackageVersionCode(getPackageName()); 
		
		if(remoteVersion > localVersion){
			
			if(remoteVersion == this.remoteVersion && file != null){
				Log.i(TAG, "Looks like apk file already downloaded but not installed");
				try {
					FileInputStream fis = new FileInputStream(this.file);
					String fileCheckSum = digestInputStream(fis);
					Log.i(TAG, "Loaded file checksum = " + this.apkMD5);
					Log.i(TAG, "OnDisk file checksum = " + fileCheckSum);
					if(fileCheckSum.equals(this.apkMD5)) {
						Log.i(TAG, "APK file that is saved on the disk is ORIGINAL");
						installApk(this.file);
					} else {
						Log.i(TAG, "APK file that is saved on the disk is NOT ORIGINAL");
						initApkLoadAndInstall(remoteVersion);
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
					initApkLoadAndInstall(remoteVersion);
				}
			} else {
				initApkLoadAndInstall(remoteVersion);
			}			
		}
	}
	
	/**
	 * 
	 * @param remoteVersion
	 */
	private void initApkLoadAndInstall(int remoteVersion){
		this.file = null;
		this.remoteVersion = -1;
		this.file = loadApkFile();
		if(this.file != null) {
			this.remoteVersion = remoteVersion;
			installApk(this.file);
		}
	}
	
	/**
	 * Returns version code for specified package
	 * returns 0 if package not found
	 * @param pkg - package name
	 * @return package versionCode or 0 if not founded
	 */
	private int getPackageVersionCode(String pkg){
		int version = 0;
		try {
			PackageManager pm = getPackageManager();
			PackageInfo info = pm.getPackageInfo(pkg, 0);
			version = info.versionCode;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		return version;
	}
	
	
	/**
	 * Returns remote version code
	 * @return versionCode
	 */
	private String loadRemoteVersionCode(){
		InputStream is = getStreamByUrl(URL_VERSION_CODE);
		if(is == null){
			Log.i(TAG, "Input stream is null");
			return null;
		}
		Scanner scanner = new Scanner(is);
		String out = scanner.useDelimiter("\\A").next();
		scanner.close();
		Log.i(TAG, out);
		return out;
	}

	
	/**
	 * Loads APK file from network 
	 * @return {@link File} if success <code>null</code> otherwise
	 */
	private File loadApkFile(){
		File file;
		try {
			InputStream is = getStreamByUrl(URL_APK);

			FileOutputStream fos;
			
			String filePath;
			
			if (isExternalAvailable()) {
				// Load to external
				filePath = Environment.getExternalStorageDirectory() + APK_DESIGNATION;
				
				Log.i(TAG, "Begin save to external storage");
			} else {
				// Load to internal
				filePath = Environment.getDataDirectory() + APK_DESIGNATION;
				Log.i(TAG, "Begin save to local storage");
			}
			
			file = new File(filePath);
			
			file.mkdirs();
			
			file = new File(file, APK_FILE_NAME);
			
			file.createNewFile();
			
			fos = new FileOutputStream(file);
			
			int read = 0;
			byte[] bytes = new byte[1024];
			
			while ((read = is.read(bytes)) != -1) {
				fos.write(bytes, 0, read);
			}
			
			FileDescriptor fd = fos.getFD();
			try {
				fd.sync();
			} catch (SyncFailedException e) {
				e.printStackTrace();
				
				Log.e(TAG, "===>NOT ENOUGH SPACE");
				
				Intent intent = new Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
				showNotification(R.string.ntfcn_title_no_disk_space, R.string.ntfcn_content_no_disk_space, intent);
				
				return null;
			} finally {
				this.apkMD5 = digestInputStream(new FileInputStream(file));
				is.close();
				fos.close();
			}
			
			Log.i(TAG, "File " + filePath + " has been saved ");
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			Log.e(TAG, e.getMessage());
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			Log.e(TAG, e.getMessage());
			return null;
		}
		
		return file;
	}
	
	
	/**
	 * 
	 * @param refTitle
	 * @param refText
	 */
	@SuppressLint("NewApi")
	private void showNotification(int refTitle, int refText, Intent intent){
		
		if(refText == 0 || refTitle == 0) return;
		
		String title = getResources().getString(refTitle);
		String text = getResources().getString(refText);
		
		if(title == null && text == null) return;
		
		PendingIntent resultPendingIntent =
			    PendingIntent.getActivity(
			    this,
			    0,
			    intent,
			    PendingIntent.FLAG_UPDATE_CURRENT
			);
		
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Builder builder = new NotificationCompat.Builder(getApplicationContext());
		builder.setSmallIcon(R.drawable.ic_launcher);
		builder.setContentTitle(title);
		builder.setContentText(text);
		builder.setContentIntent(resultPendingIntent);
		
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN){
			nm.notify(0, builder.build());
		} else {
			@SuppressWarnings("deprecation")
			Notification notification = builder.getNotification();
			nm.notify(0, notification);
		}
	}
	
	
	
	/**
	 * Launches activity that will install APK 
	 * @param file - android APK file
	 */
	private void installApk(final File file){
		Log.e(TAG, "Ask to install apk [" + file.getName() + "]");
		handler.post(new Runnable() {
			@Override
			public void run() {
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(intent);
			}
		});
	}
	
	/**
	 * Return MD5 checksum for particular 
	 * @param is
	 * @return
	 */
	private String digestInputStream(InputStream is){
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			is = new DigestInputStream(is, md);
			int bytesRead = 1;
			byte[] buffer = new byte[1024];
			while ((bytesRead = is.read(buffer)) != -1){}
			byte[] hash = md.digest();
			return new BigInteger(1, hash).toString(16);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
	/**
	 * Performs connection and returns {@link InputStream} for specified url.
	 * @param url
	 * @return {@link InputStream}
	 */
	private InputStream getStreamByUrl(String url){
		InputStream is = null;
		try {
			HttpGet get = new HttpGet(url);
			DefaultHttpClient client = new DefaultHttpClient();
			client.setRedirectHandler(new DefaultRedirectHandler());
			HttpResponse response = client.execute(get);
			
			long contentLength = response.getEntity().getContentLength();
			
			Log.i(TAG, "Content length = " + contentLength);
			
			is = response.getEntity().getContent();
		} catch (ClientProtocolException e) {
			e.printStackTrace();
			Log.e(TAG, e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			Log.e(TAG, e.getMessage());
		} 
		
		return is;
		
	}
	
	
	/**
	 * Checks if external storage available
	 * @return true if available false otherwise
	 */
	private boolean isExternalAvailable(){
		return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
	}
	
	
//	/**
//	 * Calculates available space 
//	 * @return
//	 */
//	@SuppressLint("NewApi")
//	public static long countAvailableSpace(File file){
//		
//		if(file == null) return 0;
//		
//		long available;
//		
//		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2){
//			StatFs stat = new StatFs(file.getAbsolutePath());
//			long blocks = stat.getAvailableBlocksLong();
//			long size = stat.getBlockSizeLong();
//			available = blocks * size;
//		} else {
//			available = file.getFreeSpace();
//		}
//		
//		return available;
//	}
	
}
