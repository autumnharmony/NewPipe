package org.schabi.newpipe;

import android.app.ActivityManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

import static org.schabi.newpipe.util.Constants.*;

@SuppressWarnings("all")
public class ModeActivity extends AppCompatActivity {


    private static final String TAG = "ModeActivity";
    private ServerSocket serverSocket;
    private int localPort;
    private String serviceName = "NewPipe";
    private NsdManager nsdManager;
    private NsdManager.RegistrationListener registrationListener;
    private NsdManager.DiscoveryListener discoveryListener;
    private NsdManager.ResolveListener resolveListener;


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mode);
        ConstraintLayout constraintLayout = findViewById(R.id.modeConstraintLayout);

        ImageView child = new ImageView(this);


        boolean tv = isTv();
        if (tv) {
            if (!isServiceRunning(ReceiverService.class)) {
                startService(new Intent(this, ReceiverService.class));
            }
        } else {
            if (!isServiceRunning(SenderService.class)) {
                startService(new Intent(this, SenderService.class));
            }
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        }

        child.setImageResource(tv ? R.drawable.ic_channel_black_24dp : R.drawable.ic_megaphone_black_24dp);
        //setting image position
        child.setLayoutParams(new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT));
        constraintLayout.addView(child);

    }

    // todo move to util
    private boolean isTv() {
        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }


    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }


    private void discoverServices() {
        nsdManager = (NsdManager) this.getSystemService(Context.NSD_SERVICE);
        nsdManager.discoverServices(
                HTTP_TCP, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    public int initializeServerSocket() throws IOException {
        // Initialize a server socket on the next available port.
        serverSocket = new ServerSocket(0);

        // Store the chosen port.
        int port = serverSocket.getLocalPort();
        return port;
    }

    public void registerService(final int port) {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(NEW_PIPE);
        serviceInfo.setServiceType(HTTP_TCP);
        serviceInfo.setPort(port);

        nsdManager = (NsdManager) this.getSystemService(Context.NSD_SERVICE);

        nsdManager.registerService(
                serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
    }


    public void initializeRegistrationListener() {
        registrationListener = new NsdManager.RegistrationListener() {

            @Override
            public void onServiceRegistered(final NsdServiceInfo nsdServiceInfo) {
                // Save the service name. Android may have changed it in order to
                // resolve a conflict, so update the name you initially requested
                // with the name Android actually used.
                serviceName = nsdServiceInfo.getServiceName();
            }

            @Override
            public void onRegistrationFailed(final NsdServiceInfo serviceInfo,
                                             final int errorCode) {
                // Registration failed! Put debugging code here to determine why.
            }

            @Override
            public void onServiceUnregistered(final NsdServiceInfo arg0) {
                // Service has been unregistered. This only happens when you call
                // NsdManager.unregisterService() and pass in this listener.
            }

            @Override
            public void onUnregistrationFailed(final NsdServiceInfo serviceInfo,
                                               final int errorCode) {
                // Unregistration failed. Put debugging code here to determine why.
            }
        };
    }

    public void initializeDiscoveryListener() {

        // Instantiate a new DiscoveryListener
        discoveryListener = new NsdManager.DiscoveryListener() {

            // Called as soon as service discovery begins.
            @Override
            public void onDiscoveryStarted(final String regType) {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onServiceFound(final NsdServiceInfo service) {
                // A service was found! Do something with it.
                Log.d(TAG, "Service discovery success" + service);
                if (!service.getServiceType().equals(HTTP_TCP)) {
                    // Service type is the string containing the protocol and
                    // transport layer for this service.
                    Log.d(TAG, "Unknown Service Type: " + service.getServiceType());
//                } else if (service.getServiceName().equals(serviceName)) {
//                    // The name of the service tells the user what they'd be
//                    // connecting to. It could be "Bob's Chat App".
//                    Log.d(TAG, "Same machine: " + serviceName);
                } else if (service.getServiceName().contains(NEW_PIPE)) {
                    initializeResolveListener();
                    nsdManager.resolveService(service, resolveListener);
                }
            }

            @Override
            public void onServiceLost(final NsdServiceInfo service) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
                Log.e(TAG, "service lost: " + service);
            }

            @Override
            public void onDiscoveryStopped(final String serviceType) {
                Log.i(TAG, "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(final String serviceType, final int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(final String serviceType, final int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }
        };
    }

    public void initializeResolveListener() {
        resolveListener = new NsdManager.ResolveListener() {

            @Override
            public void onResolveFailed(final NsdServiceInfo serviceInfo, final int errorCode) {
                // Called when the resolve fails. Use the error code to debug.
                Log.e(TAG, "Resolve failed: " + errorCode);
            }

            @Override
            public void onServiceResolved(final NsdServiceInfo serviceInfo) {
                Log.e(TAG, "Resolve Succeeded. " + serviceInfo);

                if (serviceInfo.getServiceName().contains(NEW_PIPE)) {
                    Log.d(TAG, serviceInfo.getHost().getHostAddress() + serviceInfo.getPort());
                    return;
                }
//                mService = serviceInfo;
                int port = serviceInfo.getPort();
                InetAddress host = serviceInfo.getHost();
            }
        };
    }
}
