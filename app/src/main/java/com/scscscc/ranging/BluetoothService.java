package com.scscscc.ranging;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

public class BluetoothService {
    private static  Handler handler;
    private static ClientThread ct;
    private static ServerThread st;
    private static InputStream mmInStream;
    private static OutputStream mmOutStream;
    private static boolean isServer;

    public static void init(Handler p_handler, boolean p_isServer) {
        handler = p_handler;
        isServer = p_isServer;
        feedback("Created bluetooth service object", false);
    }

    private static void feedback(String reason, boolean done) {
        Message msg = new Message();
        msg.what = 3;
        if (done)
            msg.arg1 = 1;
        else
            msg.arg1 = 0;
        msg.obj = reason;
        handler.sendMessage(msg);
    }

    @SuppressLint("MissingPermission")
    public static void connect(Context context) {
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                feedback("Permission not granted", true);
                return;
            }
        }
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            feedback("Device doesn't support Bluetooth", true);
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            feedback("Bluetooth not enabled", true);
            return;
        }
        if (isServer) {
            st = new ServerThread(bluetoothAdapter);
            st.start();
            return;
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            ArrayList<String> deviceNameList = new ArrayList<>();
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                deviceNameList.add(deviceName);
            }
            String[] deviceList2 = deviceNameList.toArray(new String[0]);
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Select device")
                    .setItems(deviceList2, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            String deviceName = deviceList2[which];
                            feedback(deviceName, false);
                            for (BluetoothDevice device : pairedDevices) {
                                if (device.getName().equals(deviceName)) {
                                    ct = new ClientThread(device);
                                    ct.start();
                                    break;
                                }
                            }
                        }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            feedback("No device selected", true);
                        }
                    })
            ;

            AlertDialog ad = builder.create();
            ad.show();

        } else {
            feedback("Pair your device first", true);
        }
    }

    public static void sendMessage(String txt) {
        try {
            byte[] txtbytes = txt.getBytes();
            byte[] buffer = new byte[txtbytes.length + 1];
            buffer[0] = 0;
            System.arraycopy(txtbytes, 0, buffer, 1, txtbytes.length);
            mmOutStream.write(buffer);
        } catch (IOException e) {
            feedback("Error occurred when sending dat", true);
        }
    }

    public static void sendMessage(long value) {
        try {
            byte[] valuebytes = ByteBuffer.allocate(8).putLong(value).array();
            byte[] buffer = new byte[valuebytes.length + 1];
            buffer[0] = 1;
            System.arraycopy(valuebytes, 0, buffer, 1, valuebytes.length);
            mmOutStream.write(buffer);
        } catch (IOException e) {
            feedback("Error occurred when sending dat", true);
        }
    }

    @SuppressLint("MissingPermission")
    private static class ServerThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;
        private BluetoothSocket socket;

        public ServerThread(BluetoothAdapter bluetoothAdapter) {
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord("VR Control Server", UUID.fromString("1b24725b-7f95-4cd3-9d0d-36deceff2846"));
            } catch (IOException e) {
                feedback("Socket's listen() method failed", true);
            }
            mmServerSocket = tmp;
        }

        public void run() {

            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    feedback("waiting for client", false);
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    feedback("Socket's accept() method failed", true);
                    return;
                }

                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
            feedback("Connected", false);
            try {
                mmInStream = socket.getInputStream();
            } catch (IOException e) {
                feedback("Error occurred when creating input stream", true);
                return;
            }
            // Keep listening to the InputStream until an exception occurs.
            processMessage();
        }

        void processMessage() {
            try {
                mmInStream = socket.getInputStream();
                mmOutStream = socket.getOutputStream();
            } catch (IOException e) {
                feedback("Error occurred when creating IO stream", true);
                return;
            }

            while (true) {
                try {
                    byte[] buffer = new byte[1024];
                    int numBytes = mmInStream.read(buffer);
                    byte firstByte = buffer[0];
                    byte[] data = Arrays.copyOfRange(buffer, 1, numBytes);
                    if (firstByte == 0) {
                        String readMessage = new String(data, 0, data.length);
                        feedback(readMessage, false);
                    } else if (firstByte == 1) {
                        long delta = ByteBuffer.wrap(data).getLong();
                        TheBrain.report(TheBrain.DATA_DELTA, delta);
                    }
                } catch (IOException e) {
                    feedback("Error occurred when reading dat", true);
                    try {
                        socket.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                    break;
                }
            }
        }

        void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                feedback("Could not close the connect socket", true);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private static class ClientThread extends Thread {
        private final BluetoothSocket socket;

        public ClientThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(UUID.fromString("1b24725b-7f95-4cd3-9d0d-36deceff2846"));
            } catch (IOException e) {
                feedback("Socket's create() method failed", true);
            }
            socket = tmp;
        }


        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            //bluetoothAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                feedback("Connecting", false);
                socket.connect();
                feedback("Connected", true);
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    socket.close();
                } catch (IOException closeException) {
                    feedback("Could not close the client socket", true);
                }
                return;
            }
            processMessage();
        }

        void processMessage() {
            try {
                mmInStream = socket.getInputStream();
                mmOutStream = socket.getOutputStream();
            } catch (IOException e) {
                feedback("Error occurred when creating IO stream", true);
                return;
            }

            while (true) {
                try {
                    byte[] buffer = new byte[1024];
                    int numBytes = mmInStream.read(buffer);
                    byte firstByte = buffer[0];
                    byte[] data = Arrays.copyOfRange(buffer, 1, numBytes);
                    if (firstByte == 0) {
                        String readMessage = new String(data, 0, data.length);
                        feedback(readMessage, false);
                    } else if (firstByte == 1) {
                        long delta = ByteBuffer.wrap(data).getLong();
                        TheBrain.report(TheBrain.DATA_DELTA, delta);
                    }
                } catch (IOException e) {
                    feedback("Error occurred when reading dat", true);
                    try {
                        socket.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                    break;
                }
            }
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                feedback("Could not close the client socket", true);
            }
        }
    }
}