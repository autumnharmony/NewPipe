
package org.schabi.newpipe;

import android.app.Service;
import android.content.Intent;
import android.net.nsd.NsdServiceInfo;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Enumeration;

public class SenderService extends Service {

    // Binder given to clients
    private final IBinder mBinder = new SenderBinder();
    private Socket socket;
    private PrintWriter output;
    private NsdServiceInfo serviceInfo;
    private static final String TAG = SenderService.class.getSimpleName().toString();


    public SenderService() {
        Log.i(TAG, "ctor");
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        // TODO: Return the communication channel to the service.
        return mBinder;
    }


    public class SenderBinder extends Binder {
        SenderService getService() {
            // Return this instance of LocalService so clients can call public methods
            return SenderService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    public void send(final NsdServiceInfo serviceInfo, final String video){
        Log.i(TAG, "send ");


        Runnable runnable = () -> {

            try {
                if (output == null) {
                    connect(serviceInfo);
                }

                output.println(video);
                output.flush();
            }
            catch (Exception ex) {

            }
        };

        Thread thread = new Thread(runnable);
        thread.start();

    }


    private boolean connect(NsdServiceInfo serviceInfo){
        Log.i(TAG, "connect ");

        if (serviceInfo.equals(this.serviceInfo) && socket != null && output != null) {
            return false;
        }
        else {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                socket = null;
            }

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
                InetAddress dest;
                try {
                    dest = Inet6Address.getByAddress(serviceInfo.getHost().getHostName(), serviceInfo.getHost().getAddress(), wifiInterface);
                }
                catch (Exception ex){
                    dest = serviceInfo.getHost();
                }

                socket = new Socket(dest, serviceInfo.getPort());

                OutputStream out = socket.getOutputStream();
                this.output = new PrintWriter(out);
                this.serviceInfo = serviceInfo;
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    tearDown();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                throw new RuntimeException(e);
            } finally {

            }
        }
    }

    public void disconnect(NsdServiceInfo serviceInfo) {
        try {
            tearDown();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void tearDown() throws IOException {
        if (socket!=null) {
            socket.close();
            socket = null;
        }

        if (output !=null) {
            output.close();
            output = null;
        }
    }

}
