package ca.uqac.musique2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.FragmentPagerAdapter;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.Albums;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class MainActivity extends ActionBarActivity implements
		ActionBar.TabListener {

	static final int POS_ALL = 0;
	static final int POS_ARTIST = 1;
	static final int POS_ALBUM = 2;
	static final int POS_PLAYLIST = 3;

	static final int MSG_ADD = 101;
	static final int MSG_LIST = 102;
	static final int MSG_STOP = 103;
	static final int MSG_DEL = 104;

	static final String[] PROJECTION = { MediaStore.Audio.Media.TITLE,
			MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DISPLAY_NAME,
			MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ARTIST,
			MediaStore.Audio.Media.DURATION };

	static final String SELECTION = MediaStore.Audio.Media.IS_MUSIC + " != 0";

	SectionsPagerAdapter mSectionsPagerAdapter;
	ViewPager mViewPager;
	PlaceholderFragment[] mFragments = new PlaceholderFragment[4];

	ArrayList<Map<String, String>> mListMusic = new ArrayList<Map<String, String>>();
	ArrayList<String> mListTitle = new ArrayList<String>();

	ArrayList<String> mListArtist = new ArrayList<String>();
	Map<String, List<Integer>> mIndexArtist = new HashMap<String, List<Integer>>();

	ArrayList<String> mListAlbum = new ArrayList<String>();
	Map<String, List<Integer>> mIndexAlbum = new HashMap<String, List<Integer>>();

	ArrayList<String> mListPlaying = new ArrayList<String>();

	int mCurrPos = POS_ALL;
	boolean mScanned = false;

	Messenger mService = null;
	Messenger mMessenger = new Messenger(new IncomingHandler());

	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MainActivity.MSG_LIST:
				mListPlaying = Utils.stringListFromBundle((Bundle) msg.obj);
				if (mFragments[POS_PLAYLIST] != null) {
					mFragments[POS_PLAYLIST].refreshList();
				}
				break;
			default:
				super.handleMessage(msg);
			}
		}
	}

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className,
				IBinder bindService) {
			mService = new Messenger(bindService);
		}

		public void onServiceDisconnected(ComponentName className) {
			mService = null;
		}
	};

	private boolean isBound;

	private void createUI() {
		final ActionBar actionBar = getSupportActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		mSectionsPagerAdapter = new SectionsPagerAdapter(
				getSupportFragmentManager());

		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mSectionsPagerAdapter);
		mViewPager
				.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
					@Override
					public void onPageSelected(int position) {
						actionBar.setSelectedNavigationItem(position);
					}
				});

		mViewPager.setCurrentItem(mCurrPos);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		setContentView(R.layout.activity_main);

		scan();

		createUI();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		scan();

		createUI();

		final ActionBar actionBar = getSupportActionBar();

		for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
			actionBar.addTab(actionBar.newTab()
					.setText(mSectionsPagerAdapter.getPageTitle(i))
					.setTabListener(this));
		}

		startService(new Intent(MainActivity.this, MusicService.class));
		doBindService();

		TimerTask task = new TimerTask() {

			@Override
			public void run() {
				try {
					updatePlayList();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}

		};

		Timer timer = new Timer();
		timer.schedule(task, 1000, 700);

		IntentFilter filter = new IntentFilter(MusicService.QUITINTENT);
		registerReceiver(new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				MainActivity.this.finish();
			}

		}, filter);
	}

	@Override
	public void onTabSelected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
		mCurrPos = tab.getPosition();
		mViewPager.setCurrentItem(tab.getPosition());
	}

	@Override
	public void onTabUnselected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
	}

	@Override
	public void onTabReselected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
	}

	private void scan() {
		mListMusic.clear();
		mListTitle.clear();

		mListArtist.clear();
		mIndexArtist.clear();

		mListAlbum.clear();
		mIndexAlbum.clear();

		Cursor cursor = getContentResolver().query(
				MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, PROJECTION,
				SELECTION, null, null);
		int index = 0;
		while (cursor.moveToNext()) {
			HashMap<String, String> music = new HashMap<String, String>();
			for (String item : PROJECTION) {
				music.put(item,
						cursor.getString(cursor.getColumnIndexOrThrow(item)));
			}
			mListMusic.add(music);
			mListTitle.add(Utils.formatMusic(music));

			List<Integer> templist;
			String artist = music.get(MediaStore.Audio.Media.ARTIST);
			if (mIndexArtist.containsKey(artist)) {
				templist = mIndexArtist.get(artist);
			} else {
				templist = new ArrayList<Integer>();
				mIndexArtist.put(artist, templist);
				mListArtist.add(artist);
			}
			templist.add(index);

			String album = music.get(MediaStore.Audio.Media.ALBUM);
			if (mIndexAlbum.containsKey(album)) {
				templist = mIndexAlbum.get(album);
			} else {
				templist = new ArrayList<Integer>();
				mIndexAlbum.put(album, templist);
				mListAlbum.add(album);
			}
			templist.add(index);

			++index;
		}

		cursor.close();

		mScanned = true;
	}

	List<String> getList(int pos) {
		List<String> listToShow;

		switch (pos) {
		case POS_ALL:
			listToShow = mListTitle;
			break;
		case POS_ARTIST:
			listToShow = mListArtist;
			break;
		case POS_ALBUM:
			listToShow = mListAlbum;
			break;
		case POS_PLAYLIST:
			listToShow = mListPlaying;
			break;
		default:
			listToShow = mListTitle;
		}

		return listToShow;
	}

	void action(int type, int pos) throws RemoteException {
		if (!CheckIfServiceIsRunning()) {
			return;
		}

		if (type == POS_ALL) {
			Map<String, String> music = mListMusic.get(pos);
			mService.send(Message.obtain(null, MSG_ADD,
					Utils.mapToBundle(music)));
		} else if (type == POS_ARTIST) {
			String artist = mListArtist.get(pos);
			List<Integer> listMusic = mIndexArtist.get(artist);
			if (artist == null) {
				return;
			}
			for (int n : listMusic) {
				mService.send(Message.obtain(null, MSG_ADD,
						Utils.mapToBundle(mListMusic.get(n))));
			}
		} else if (type == POS_ALBUM) {
			String album = mListAlbum.get(pos);
			List<Integer> listMusic = mIndexAlbum.get(album);
			if (album == null) {
				return;
			}
			for (int n : listMusic) {
				mService.send(Message.obtain(null, MSG_ADD,
						Utils.mapToBundle(mListMusic.get(n))));
			}
		} else if (type == POS_PLAYLIST) {
			mService.send(Message.obtain(null, MSG_DEL, Utils.intToBundle(pos)));
		}
		updatePlayList();
	}

	private boolean CheckIfServiceIsRunning() {
		if (isBound) {
			return true;
		}
		if (!MusicService.isRunning()) {
			startService(new Intent(MainActivity.this, MusicService.class));
		}
		doBindService();

		return isBound;
	}

	void updatePlayList() throws RemoteException {
		if (!CheckIfServiceIsRunning()) {
			return;
		}
		Message m = Message.obtain(null, MSG_LIST, null);
		m.replyTo = mMessenger;
		mService.send(m);
	}

	void doBindService() {
		isBound = getApplicationContext().bindService(
				new Intent(getApplicationContext(), MusicService.class),
				mConnection, Context.BIND_AUTO_CREATE);
	}

	void doUnbindService() {
		if (isBound) {
			getApplicationContext().unbindService(mConnection);
			isBound = false;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		doUnbindService();
	}

	/**
	 * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
	 * one of the sections/tabs/pages.
	 */
	public class SectionsPagerAdapter extends PagerAdapter {
		FragmentManager fragmentManager;
		Fragment[] fragments;

		public SectionsPagerAdapter(FragmentManager fm) {
			fragmentManager = fm;
			fragments = new Fragment[4];
		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			FragmentTransaction trans = fragmentManager.beginTransaction();
			trans.remove(fragments[position]);
			trans.commit();
			fragments[position] = null;
		}

		@Override
		public Fragment instantiateItem(ViewGroup container, int position) {
			Fragment fragment = getItem(position);
			FragmentTransaction trans = fragmentManager.beginTransaction();
			trans.add(container.getId(), fragment, "fragment:" + position);
			trans.commit();
			return fragment;
		}

		public Fragment getItem(int position) {
			if (fragments[position] == null) {
				fragments[position] = PlaceholderFragment.newInstance(position);
			}
			return fragments[position];

		}

		@Override
		public int getCount() {
			return 4;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			Locale l = Locale.getDefault();
			switch (position) {
			case POS_ALL:
				return getString(R.string.title_section1).toUpperCase(l);
			case POS_ARTIST:
				return getString(R.string.title_section2).toUpperCase(l);
			case POS_ALBUM:
				return getString(R.string.title_section3).toUpperCase(l);
			case POS_PLAYLIST:
				return getString(R.string.title_section4).toUpperCase(l);
			}
			return null;
		}

		@Override
		public boolean isViewFromObject(View view, Object fragment) {
			return ((Fragment) fragment).getView() == view;
		}
	}

	public static class PlaceholderFragment extends Fragment {

		ListView mListViewMusic;
		int mPos;

		public static final PlaceholderFragment newInstance(int sectionNumber) {
			PlaceholderFragment fragment = new PlaceholderFragment();
			Bundle bundle = new Bundle(1);
			bundle.putInt("pos", sectionNumber);
			fragment.setArguments(bundle);
			return fragment;
		}

		public void refreshList() {
			if (getActivity() == null) {
				return;
			}
			ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(
					getActivity(), android.R.layout.simple_list_item_1,
					((MainActivity) getActivity()).getList(mPos));

			mListViewMusic.setAdapter(arrayAdapter);
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container,
					false);

			Bundle bundle = this.getArguments();
			mPos = bundle.getInt("pos");

			((MainActivity) getActivity()).mFragments[mPos] = this;

			mListViewMusic = (ListView) rootView
					.findViewById(R.id.listViewMusic);

			refreshList();

			mListViewMusic.setOnItemClickListener(new OnItemClickListener() {

				@Override
				public void onItemClick(AdapterView<?> parent, View view,
						int position, long id) {
					try {
						((MainActivity) getActivity()).action(mPos, position);
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}

			});
			return rootView;
		}
	}
}
