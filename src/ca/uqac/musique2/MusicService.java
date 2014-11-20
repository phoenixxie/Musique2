package ca.uqac.musique2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class MusicService extends Service {
	private static final String TAG = MusicService.class.getSimpleName();

	public static final String QUITINTENT = "ca.uqac.musique2.QUITQUITQUITQUIT";

	MediaPlayer mPlayer = new MediaPlayer();
	private static boolean isRunning = false;
	PlayThread mPlayThread;

	List<Map<String, String>> mListPlay = new ArrayList<Map<String, String>>();
	int mNextPosition = 0;

	Messenger mMessenger = new Messenger(new IncomingHandler(this));

	NotificationManager mNManager;
	NotificationCompat.Builder mNBuilder;
	BroadcastReceiver mReceiver;

	static class IncomingHandler extends Handler {
		MusicService parent;

		public IncomingHandler(MusicService service) {
			parent = service;
		}

		@Override
		public void handleMessage(Message msg) {

			switch (msg.what) {
			case MainActivity.MSG_ADD:
				parent.addList(Utils.mapFromBundle((Bundle) msg.obj));
				break;
			case MainActivity.MSG_LIST:
				ArrayList<String> musicList = parent.dumpList();
				Message response = Message.obtain(null, MainActivity.MSG_LIST,
						Utils.stringListToBundle(musicList));
				try {
					msg.replyTo.send(response);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				break;
			case MainActivity.MSG_DEL:
				int pos = Utils.intFromBundle((Bundle) msg.obj);
				parent.delFromList(pos);
				break;
			case MainActivity.MSG_STOP:
				parent.stop();
				break;

			default:
				super.handleMessage(msg);
			}
		}
	}

	private synchronized void addList(Map<String, String> music) {
		mListPlay.add(music);
	}

	private synchronized void delFromList(int pos) {
		if (pos >= mListPlay.size()) {
			return;
		}

		mListPlay.remove(pos);
		if (pos + 1 < mNextPosition) {
			--mNextPosition;
		} else if (pos + 1 == mNextPosition) {
			--mNextPosition;
			mPlayer.stop();
		}
	}

	private void stop() {
		mPlayer.reset();
		mPlayer.release();
	}

	@SuppressWarnings("unchecked")
	private synchronized ArrayList<String> dumpList() {
		ArrayList<String> newList = new ArrayList<String>();

		int n = 0;
		for (Map<String, String> music : mListPlay) {
			if (n + 1 == mNextPosition) {
				newList.add(Utils.formatMusic(music, true));
			} else {
				newList.add(Utils.formatMusic(music));
			}
			++n;
		}

		return newList;
	}

	public void playList(List<String> listMusic) {
		stop();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mMessenger.getBinder();
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mPlayThread = new PlayThread();
		mPlayThread.start();
		Log.i(TAG, "Service creating");

		Intent i = new Intent(QUITINTENT);
		PendingIntent contentIntent = PendingIntent.getBroadcast(this, 0, i, 0);
		mNManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mNBuilder = new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.ic_launcher).setContentIntent(contentIntent);
		
		IntentFilter filter = new IntentFilter(QUITINTENT);
		mReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				MusicService.this.stopService(new Intent(MusicService.this, MusicService.class));
			}

		};
		
		registerReceiver(mReceiver, filter);

		isRunning = true;
	}

	public void updateNotification(String music) {
		mNBuilder.setContentTitle("Playing `" + music + "'").setContentText("Click here to quit!");

		Notification note = mNBuilder.build();
		note.flags |= Notification.FLAG_NO_CLEAR;

		startForeground(R.string.app_name, note);
	}

	@Override
	public void onDestroy() {
		mPlayThread.running = false;

		if (mPlayer != null) {
			mPlayer.reset();
			mPlayer.release();
		}

		try {
			mPlayThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		unregisterReceiver(mReceiver);

		Log.i(TAG, "Service destroying");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_NOT_STICKY;
	}

	public static boolean isRunning() {
		return isRunning;
	}

	class PlayThread extends Thread {
		boolean running = true;

		public void run() {
			Map<String, String> music;
			while (running) {
				music = null;
				if (!mPlayer.isPlaying()) {

					synchronized (MusicService.this) {
						if (mListPlay.size() > mNextPosition) {
							music = mListPlay.get(mNextPosition);
							++mNextPosition;
						}
					}
					if (music != null) {
						try {
							mPlayer.reset();
							mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
							mPlayer.setDataSource(MusicService.this, Uri
									.parse(music
											.get(MediaStore.Audio.Media.DATA)));
							mPlayer.prepare();
							mPlayer.setLooping(false);
							mPlayer.start();
							updateNotification(music
									.get(MediaStore.Audio.Media.TITLE));
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}

				try {
					Thread.sleep(400);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
