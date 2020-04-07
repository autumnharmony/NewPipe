package org.schabi.newpipe;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import org.schabi.newpipe.util.Constants;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * SenderActivity class is for sending commands to another device's instance of NewPipe
 */
public class SenderActivity extends AppCompatActivity {

    public static final String DISCOVERED = "org.schabi.newpipe.DISCOVERED";
    /**
     * Removes invisible separators (\p{Z}) and punctuation characters including
     * brackets (\p{P}). See http://www.regular-expressions.info/unicode.html for
     * more details.
     */
    protected final static String REGEX_REMOVE_FROM_URL = "[\\p{Z}\\p{P}]";
    protected final String TAG = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
    private SenderService senderService;
    boolean mBound = false;
    private String url;
    private ListView listView;
    private List<NsdServiceInfo> list;
    private ArrayAdapter<NsdServiceInfo> adapter;
    private NsdManager nsdManager;

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            SenderService.SenderBinder binder = (SenderService.SenderBinder) service;
            senderService = binder.getService();
            mBound = true;
            notifyUser("onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            notifyUser("onServiceDisconnected");
        }
    };
//    private NsdWrapper nsdWrapper;

    public SenderActivity() {
        Log.i(TAG, "Sender Actitivy ctor");
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Sender Activity onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sender);

        nsdManager = getSystemService(NsdManager.class);
        if (!isChangingConfigurations()) {

            final String videoUrl = getUrl(getIntent());
            saveUrl(videoUrl);

            listView = findViewById(R.id.receivers);
            list = new ArrayList<>();
            adapter = new ServiceInfoAdapter(this, R.layout.list_receiver_item, list);
            listView.setAdapter(adapter);


            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> arg0, View v, int position, long arg3) {
                    NsdServiceInfo selected = list.get(position);
                    nsdManager.resolveService(selected, new NsdManager.ResolveListener() {
                        @Override
                        public void onResolveFailed(NsdServiceInfo nsdServiceInfo, int i) {

                            Log.e(TAG, "error:" + i);
                        }

                        @Override
                        public void onServiceResolved(NsdServiceInfo nsdServiceInfo) {
                            senderService.send(nsdServiceInfo, url);
                            finish();
                        }
                    });
                }
            });

            NsdManager nsdManager = this.getSystemService(NsdManager.class);

            Set<NsdServiceInfo> serviceInfoSet = new HashSet<>();

            nsdManager.discoverServices(Constants.HTTP_TCP, NsdManager.PROTOCOL_DNS_SD, new NsdManager.DiscoveryListener() {
                @Override
                public void onStartDiscoveryFailed(String s, int i) {

                }

                @Override
                public void onStopDiscoveryFailed(String s, int i) {

                }

                @Override
                public void onDiscoveryStarted(String s) {

                }

                @Override
                public void onDiscoveryStopped(String s) {

                }

                @Override
                public void onServiceFound(NsdServiceInfo serviceInfo) {
                    Timer t = new Timer();
                    t.schedule(new TimerTask() {
                        public void run() {

                            Log.d("timer", "timer");
                            runOnUiThread(new Runnable() {

                                @Override
                                public void run() {
                                    if (!serviceInfoSet.contains(serviceInfo)) {
                                        serviceInfoSet.add(serviceInfo);
                                        adapter.add(serviceInfo);
                                    }
                                }
                            });
                        }
                    }, 10);
                }

                @Override
                public void onServiceLost(NsdServiceInfo nsdServiceInfo) {

                }
            });
        }
    }

    private void notifyUser(String s) {
        Log.i(TAG, s);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!isChangingConfigurations()) {

            if (!mBound) {
                // Bind to LocalService
                Intent intent = new Intent(this, SenderService.class);
                bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            }

        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        //Unregister services
//        Intent intent = new Intent(this, DiscoverService.class);
//        stopService(intent);

        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }


    private String getDeviceIpAddress(WifiManager wifi) {
        WifiInfo connectionInfo = wifi.getConnectionInfo();
        int ipAddress = connectionInfo.getIpAddress();
        String s = android.text.format.Formatter.formatIpAddress(ipAddress);
        return s;
    }

    protected void saveUrl(String url) {
        this.url = url;
    }

    protected String getUrl(Intent intent) {
        // first gather data and find service
        String videoUrl = null;
        if (intent.getData() != null) {
            // this means the video was called though another app
            videoUrl = intent.getData().toString();
        } else if (intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
            //this means that vidoe was called through share menu
            String extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
            videoUrl = getUris(extraText)[0];
        }

        return videoUrl;
    }

    protected String removeHeadingGibberish(final String input) {
        int start = 0;
        for (int i = input.indexOf("://") - 1; i >= 0; i--) {
            if (!input.substring(i, i + 1).matches("\\p{L}")) {
                start = i + 1;
                break;
            }
        }
        return input.substring(start, input.length());
    }

    protected String trim(final String input) {
        if (input == null || input.length() < 1) {
            return input;
        } else {
            String output = input;
            while (output.length() > 0 && output.substring(0, 1).matches(REGEX_REMOVE_FROM_URL)) {
                output = output.substring(1);
            }
            while (output.length() > 0
                    && output.substring(output.length() - 1, output.length()).matches(REGEX_REMOVE_FROM_URL)) {
                output = output.substring(0, output.length() - 1);
            }
            return output;
        }
    }

    /**
     * Retrieves all Strings which look remotely like URLs from a text.
     * Used if NewPipe was called through share menu.
     *
     * @param sharedText text to scan for URLs.
     * @return potential URLs
     */
    protected String[] getUris(final String sharedText) {
        final Collection<String> result = new HashSet<>();
        if (sharedText != null) {
            final String[] array = sharedText.split("\\p{Space}");
            for (String s : array) {
                s = trim(s);
                if (s.length() != 0) {
                    if (s.matches(".+://.+")) {
                        result.add(removeHeadingGibberish(s));
                    } else if (s.matches(".+\\..+")) {
                        result.add("http://" + s);
                    }
                }
            }
        }
        return result.toArray(new String[result.size()]);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    public static class ViewHolder {
        public TextView display_name;
        public TextView display_number;

    }

    class ServiceInfoAdapter extends ArrayAdapter<NsdServiceInfo> {

        private Activity activity;
        private List<NsdServiceInfo> serviceInfos;
        private LayoutInflater inflater;

        public ServiceInfoAdapter(Context context, int resource, List<NsdServiceInfo> objects) {
            super(context, resource, objects);
            inflater = LayoutInflater.from(context);
            serviceInfos = objects;
        }

        public int getCount() {
            return serviceInfos.size();
        }

        public NsdServiceInfo getItem(int position) {
            return serviceInfos.get(position);
        }

        public long getItemId(int position) {
            return position;
        }


        public View getView(int position, View convertView, ViewGroup parent) {
            View vi = convertView;
            final ViewHolder holder;
            try {
                if (convertView == null) {
                    vi = inflater.inflate(R.layout.list_receiver_item, null);
                    holder = new ViewHolder();

                    holder.display_name = (TextView) vi.findViewById(R.id.list_receiver_item_name);
//                    holder.display_number = (TextView) vi.findViewById(R.id.display_number);
                    vi.setTag(holder);
                } else {
                    holder = (ViewHolder) vi.getTag();
                }

                holder.display_name.setText(serviceInfos.get(position).getServiceName());
//                holder.display_number.setText(serviceInfos.get(position).number);


            } catch (Exception e) {


            }
            return vi;
        }

    }

    class RunVideoTask extends AsyncTask<Object, Void, Void> {

        @Override
        protected Void doInBackground(Object... params) {
            NsdServiceInfo selected = (NsdServiceInfo) params[0];
            String video = (String) params[1];


            Socket socket = null;

            try {

                Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
                NetworkInterface wifiInterface = null;
                while (networkInterfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = networkInterfaces.nextElement();
                    if (networkInterface.getDisplayName().equals("wlan0") || networkInterface.getDisplayName().equals("eth0")) {
                        wifiInterface = networkInterface;
                        break;
                    }
                }
//                Inet6Address dest = Inet6Address.getByAddress(selected.getHost().getHostName(), selected.getHost().getAddress(), wifiInterface);
//                socket = new Socket(dest, selected.getPort());


                OutputStream out = socket.getOutputStream();
                PrintWriter output = new PrintWriter(out);
                output.println(video);
                output.flush();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            finish();
        }
    }
}
