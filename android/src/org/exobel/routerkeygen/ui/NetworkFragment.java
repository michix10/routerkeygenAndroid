/*
 * Copyright 2012 Rui Araújo, Luís Fonseca
 *
 * This file is part of Router Keygen.
 *
 * Router Keygen is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Router Keygen is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Router Keygen.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.exobel.routerkeygen.ui;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.exobel.routerkeygen.AutoConnectService;
import org.exobel.routerkeygen.R;
import org.exobel.routerkeygen.algorithms.Keygen;
import org.exobel.routerkeygen.algorithms.NativeThomson;
import org.exobel.routerkeygen.algorithms.ThomsonKeygen;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

@SuppressWarnings("deprecation")
public class NetworkFragment extends SherlockFragment {

	public static final String NETWORK_ID = "vulnerable_network";
	public static final String TAG = "NetworkFragment";
	private Keygen keygen;
	private KeygenThread thread;
	private ViewSwitcher root;
	private TextView messages;
	private List<String> passwordList;

	public NetworkFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getArguments().containsKey(NETWORK_ID)) {
			keygen = (Keygen) getArguments().getParcelable(NETWORK_ID);
			thread = new KeygenThread(keygen);
		}
		if (savedInstanceState != null) {
			String[] passwords = savedInstanceState
					.getStringArray(PASSWORD_LIST);
			if (passwords != null) {
				passwordList = new ArrayList<String>();
				for (String p : passwords)
					passwordList.add(p);
			}
		}
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		root = (ViewSwitcher) inflater.inflate(R.layout.fragment_network,
				container, false);
		messages = (TextView) root.findViewById(R.id.loading_text);
		final View autoConnect = root.findViewById(R.id.auto_connect);
		// Auto connect service unavailable for manual calculations
		if (keygen.getScanResult() == null)
			autoConnect.setVisibility(View.GONE);
		else
			autoConnect.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					if (passwordList == null)
						return;
					if (isAutoConnectServiceRunning()) {
						Toast.makeText(getActivity(),
								R.string.msg_auto_connect_running,
								Toast.LENGTH_SHORT).show();
						return;
					}
					Intent i = new Intent(getActivity(),
							AutoConnectService.class);
					i.putStringArrayListExtra(AutoConnectService.KEY_LIST,
							(ArrayList<String>) passwordList);
					i.putExtra(AutoConnectService.SCAN_RESULT,
							keygen.getScanResult());
					getActivity().startService(i);
				}
			});
		if (passwordList != null)
			displayResults();
		return root;
	}

	private boolean isAutoConnectServiceRunning() {
		if (getActivity() == null)
			return false;
		final ActivityManager manager = (ActivityManager) getActivity()
				.getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager
				.getRunningServices(Integer.MAX_VALUE)) {
			if ("org.exobel.routerkeygen.AutoConnectService"
					.equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	private static final String PASSWORD_LIST = "password_list";

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (passwordList != null)
			outState.putStringArray(PASSWORD_LIST,
					passwordList.toArray(new String[0]));
	}

	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		if (passwordList == null)
			thread.execute();
	}

	@Override
	public void onDetach() {
		super.onDetach();
		thread.cancel();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.share_keys, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_share:
			try {
				if (passwordList == null)
					return true;
				Intent i = new Intent(Intent.ACTION_SEND);
				i.setType("text/plain");
				i.putExtra(Intent.EXTRA_SUBJECT, keygen.getSsidName()
						+ getString(R.string.share_msg_begin));
				final StringBuilder message = new StringBuilder(
						keygen.getSsidName());
				message.append(getString(R.string.share_msg_begin));
				message.append(":\n");
				for (String password : passwordList) {
					message.append(password);
					message.append('\n');
				}
				i.putExtra(Intent.EXTRA_TEXT, message.toString());
				startActivity(Intent.createChooser(i,
						getString(R.string.share_title)));
			} catch (Exception e) {
				Toast.makeText(getActivity(), R.string.msg_err_sendto,
						Toast.LENGTH_SHORT).show();
			}
			return true;
		case R.id.menu_save_sd:
			if (!Environment.getExternalStorageState().equals(
					Environment.MEDIA_MOUNTED)) {
				Toast.makeText(getActivity(), R.string.msg_nosdcard,
						Toast.LENGTH_SHORT).show();
				return true;
			}
			if (passwordList == null)
				return true;
			final StringBuilder message = new StringBuilder(
					keygen.getSsidName());
			message.append(" KEYS\n");
			for (String password : passwordList) {
				message.append(password);
				message.append('\n');
			}
			try {

				final BufferedWriter out = new BufferedWriter(new FileWriter(
						folderSelect + File.separator + keygen.getSsidName()
								+ ".txt"));
				out.write(message.toString());
				out.close();
			} catch (IOException e) {
				Toast.makeText(getActivity(),
						getString(R.string.msg_err_saving_key_file),
						Toast.LENGTH_SHORT).show();
				return true;
			}
			Toast.makeText(
					getActivity(),
					keygen.getSsidName() + ".txt "
							+ getString(R.string.msg_saved_key_file),
					Toast.LENGTH_SHORT).show();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void displayResults() {
		if (passwordList.isEmpty()) {
			root.findViewById(R.id.loading_spinner).setVisibility(View.GONE);
			messages.setText(R.string.msg_no_correct_keys);
		} else {
			final ListView list = (ListView) root.findViewById(R.id.list_keys);
			list.setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> parent, View view,
						int position, long id) {
					final String key = ((TextView) view).getText().toString();
					Toast.makeText(getActivity(),
							getString(R.string.msg_copied, key),
							Toast.LENGTH_SHORT).show();
					ClipboardManager clipboard = (ClipboardManager) getActivity()
							.getSystemService(Context.CLIPBOARD_SERVICE);

					clipboard.setText(key);
					startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
				}
			});
			list.setAdapter(new ArrayAdapter<String>(getActivity(),
					android.R.layout.simple_list_item_1, passwordList));
			root.showNext();
		}
	}

	private class KeygenThread extends AsyncTask<Keygen, Integer, List<String>> {
		private Keygen keygen;

		private KeygenThread(Keygen keygen) {
			this.keygen = keygen;
		}

		@Override
		protected void onPostExecute(List<String> result) {
			if (getActivity() == null)
				return;
			if (result == null)
				return;
			passwordList = result;
			displayResults();
		}

		@Override
		protected void onPreExecute() {
			if (!keygen.isSupported()) {
				root.findViewById(R.id.loading_spinner)
						.setVisibility(View.GONE);
				messages.setText(R.string.msg_unspported);
				cancel(true);
			}
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			if (getActivity() == null)
				return;
			for (int i = 0; i < values.length; i += 2) {
				switch (values[i]) {
				case SHOW_TOAST:
					Toast.makeText(getActivity(), values[i + 1],
							Toast.LENGTH_SHORT).show();

					break;
				case SHOW_MESSAGE_NO_SPINNER:
					messages.setText(values[i + 1]);
					root.findViewById(R.id.loading_spinner).setVisibility(
							View.GONE);
					break;

				case SHOW_MESSAGE_WITH_SPINNER:
					messages.setText(values[i + 1]);
					root.findViewById(R.id.loading_spinner).setVisibility(
							View.VISIBLE);
					break;

				}
			}
		}

		public void cancel() {
			keygen.setStopRequested(true);
			cancel(true);
		}

		private final static int SHOW_TOAST = 0;
		private final static int SHOW_MESSAGE_WITH_SPINNER = 1;
		private final static int SHOW_MESSAGE_NO_SPINNER = 2;

		@Override
		protected List<String> doInBackground(Keygen... params) {
			if (keygen instanceof ThomsonKeygen) {
				getPrefs();
				((ThomsonKeygen) keygen).setDictionary(folderSelect);
				((ThomsonKeygen) keygen).setInternetAlgorithm(thomson3g);
				((ThomsonKeygen) keygen).setWebdic(getActivity().getResources()
						.openRawResource(R.raw.webdic));
			}
			List<String> result = calcKeys();
			if (nativeCalc && (keygen instanceof ThomsonKeygen)) {
				if (((ThomsonKeygen) keygen).isErrorDict()) {
					publishProgress(SHOW_MESSAGE_WITH_SPINNER,
							R.string.msg_startingnativecalc);
					try {
						keygen = new NativeThomson(keygen);
						if (isCancelled())
							return null;
						result = calcKeys();
					} catch (LinkageError e) {
						publishProgress(SHOW_MESSAGE_NO_SPINNER,
								R.string.err_misbuilt_apk);
						return null;
					}
				}
			}
			return result;
		}

		private List<String> calcKeys() {
			long begin = System.currentTimeMillis();
			final List<String> result = keygen.getKeys();
			long end = System.currentTimeMillis() - begin;
			Log.d(TAG, "Time to solve:" + end);

			final int errorCode = keygen.getErrorCode();
			if (errorCode != 0) {
				if (result == null)
					publishProgress(SHOW_MESSAGE_NO_SPINNER, errorCode);
				else
					publishProgress(SHOW_TOAST, errorCode);
			}
			return result;
		}

	}

	private boolean thomson3g;
	private boolean nativeCalc;
	private String folderSelect;

	private void getPrefs() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(getActivity());
		thomson3g = prefs.getBoolean(Preferences.thomson3gPref, false);
		nativeCalc = prefs.getBoolean(Preferences.nativeCalcPref, true);
		folderSelect = prefs.getString(Preferences.dicLocalPref, Environment
				.getExternalStorageDirectory().getAbsolutePath());
	}

}
