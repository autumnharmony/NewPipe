package org.schabi.newpipe;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.util.NavigationHelper;

import static org.schabi.newpipe.util.Constants.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class ReceiverService extends Service {

    private final static String TAG = "ReceiverService";

    public ReceiverService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onCreate() {
        super.onCreate();
        Log.v("service", "listeningService started");

        String deviceName;
        deviceName = Build.MODEL;
        Log.i("btAddress", deviceName);


//        nsdHelper = new NsdWrapperNative.NsdHelper(getApplicationContext(), deviceName + " Receiver");
//        nsdHelper.initializeNsd();
        new ReceiverServiceSocketThread(this, getSystemService(NsdManager.class)).start();
    }

    static class ReceiverServiceClientSocketThread extends Thread {
        private ReceiverService receiverService;
        final Socket mSocket;
        final BufferedReader mInputReader;
        final OutputStream mOutputStream;

        public ReceiverServiceClientSocketThread(ReceiverService receiverService, Socket socket) throws IOException {
            this.receiverService = receiverService;
            Log.v("socket", "new client socket received: " + socket.toString());
            this.mSocket = socket;
            this.mInputReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
            this.mOutputStream = mSocket.getOutputStream();
        }

        @Override
        public void run() {
            super.run();
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String data = mInputReader.readLine();
                    if (data == null) throw new IOException("Connection closed");
                    Log.v("input", "data received: " + data);
                    Context context = receiverService.getApplicationContext();
                    Intent intentByLink = null;
                    try {
                        intentByLink = NavigationHelper.getIntentByLink(context, data);
                        intentByLink.putExtra(VideoDetailFragment.AUTO_PLAY, true);
                        intentByLink.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intentByLink.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        context.startActivity(intentByLink);
                    } catch (ExtractionException e) {
                        e.printStackTrace();
                    }
                } catch (IOException e) {

                    break;
                }
            }
            Log.v("socket", "client socket closed: " + mSocket.toString());
        }
    }

    static class ReceiverServiceSocketThread extends Thread {

        private ReceiverService receiverService;
        ServerSocket mServerSocket = null;
        private final NsdManager nsdManager;

        public ReceiverServiceSocketThread(ReceiverService receiverService, NsdManager nsdManager) {
            this.receiverService = receiverService;
            this.nsdManager = nsdManager;
        }

        @Override
        public void run() {
            super.run();
            try {
                mServerSocket = new ServerSocket(0);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            int localPort = mServerSocket.getLocalPort();
            InetAddress address = mServerSocket.getInetAddress();
            Log.v("socket", "server socket started at: " + mServerSocket.getLocalPort());

            NsdServiceInfo serviceInfo = new NsdServiceInfo();
            serviceInfo.setPort(localPort);
            serviceInfo.setHost(address);
            serviceInfo.setServiceName(NEW_PIPE);
            serviceInfo.setServiceType(HTTP_TCP);

            nsdManager.registerService(
                    serviceInfo, NsdManager.PROTOCOL_DNS_SD, new NsdManager.RegistrationListener() {
                        @Override
                        public void onRegistrationFailed(NsdServiceInfo nsdServiceInfo, int i) {
                            Log.w(TAG, "RegistrationFailed");
                        }

                        @Override
                        public void onUnregistrationFailed(NsdServiceInfo nsdServiceInfo, int i) {
                            Log.w(TAG, "UnregistrationFailed");
                        }

                        @Override
                        public void onServiceRegistered(NsdServiceInfo nsdServiceInfo) {
                            Log.i(TAG, "ServiceRegistered");
                            Log.i(TAG, nsdServiceInfo.getServiceName());
                        }

                        @Override
                        public void onServiceUnregistered(NsdServiceInfo nsdServiceInfo) {
                            Log.i(TAG, "ServiceUnregistered");
                        }
                    });

            while (!Thread.currentThread().isInterrupted()) {
                Socket socket = null;
                try {
                    socket = mServerSocket.accept();
                    new ReceiverServiceClientSocketThread(receiverService, socket).start();
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }

        }
    }
}
