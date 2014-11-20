package ca.uqac.musique2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;

public class Utils {
	public static Bundle stringListToBundle(ArrayList<String> list) {
		Bundle output = new Bundle();
		output.putStringArrayList("data", list);
		return output;
	}
	
	public static ArrayList<String> stringListFromBundle(Bundle input) {
		return input.getStringArrayList("data");
	}
	
	public static Bundle intToBundle(int n) {
		Bundle output = new Bundle();
		output.putInt("data", n);
		return output;
	}
	
	public static int intFromBundle(Bundle input) {
		return input.getInt("data");
	}
	
	
	public static Bundle intListToBundle(ArrayList<Integer> list) {
		Bundle output = new Bundle();
		output.putIntegerArrayList("data", list);
		return output;
	}
	
	public static ArrayList<Integer> intListFromBundle(Bundle input) {
		return input.getIntegerArrayList("data");
	}
	
	public static Bundle mapToBundle(Map<String, String> input) {
		Bundle output = new Bundle();
		for (String key : input.keySet()) {
			output.putString(key, input.get(key));
		}
		return output;
	}

	public static Map<String, String> mapFromBundle(
			Bundle input) {
		Map<String, String> output = new HashMap<String, String>();
		for (String key : input.keySet()) {
			output.put(key, input.getString(key));
		}
		return output;
	}
	
//	static final String[] PROJECTION = { MediaStore.Audio.Media.TITLE,
//		MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DISPLAY_NAME,
//		MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ARTIST,
//		MediaStore.Audio.Media.DURATION };
	
	public static String formatMusic(Map<String, String> music, boolean playing) {
		StringBuilder sb = new StringBuilder();
		sb.append(music.get(MediaStore.Audio.Media.TITLE))
		.append(" - ").append(music.get(MediaStore.Audio.Media.ALBUM))
		.append(" / ").append(music.get(MediaStore.Audio.Media.ARTIST));
		if (playing) {
			sb.append(" #Playing");
		}
		return sb.toString();
	}
	
	public static String formatMusic(Map<String, String> music) {
		return formatMusic(music, false);
	}
}
