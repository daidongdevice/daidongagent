package com.daidong.hermesstandalone;

import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.Bundle;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ShizukuUserService extends Binder {
    public static final int MSG_EXEC = 1;
    public static final int MSG_RESULT = 2;

    private final Handler handler = new Handler(new Handler.Callback() {
        public boolean handleMessage(Message msg) {
            if (msg.what == MSG_EXEC) {
                Bundle data = msg.getData();
                data.setClassLoader(ShizukuUserService.class.getClassLoader());
                String command = data.getString("command");
                Messenger replyTo = msg.replyTo;
                String result = execCommand(command);
                if (replyTo != null) {
                    try {
                        Message reply = Message.obtain(null, MSG_RESULT);
                        Bundle replyData = new Bundle();
                        replyData.putString("result", result);
                        reply.setData(replyData);
                        replyTo.send(reply);
                    } catch (RemoteException e) {
                        // client may have disconnected
                    }
                }
                return true;
            }
            return false;
        }
    });

    private final Messenger messenger = new Messenger(handler);
    private final IBinder target = messenger.getBinder();

    public ShizukuUserService() {
        super();
    }

    // Constructor for Shizuku v13+
    public ShizukuUserService(android.content.Context context) {
        super();
    }

    @Override
    public String getInterfaceDescriptor() {
        try {
            return target.getInterfaceDescriptor();
        } catch (RemoteException e) {
            return null;
        }
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == 16777114) { // destroy
            Log.d("DaidongAgent", "ShizukuUserService destroy called");
            System.exit(0);
            return true;
        }
        if (code == 12345) { // EXEC
            String command = data.readString();
            String result = execCommand(command);
            reply.writeString(result);
            return true;
        }
        return target.transact(code, data, reply, flags);
    }

    private String execCommand(String command) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", command});
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line).append("\n");
            while ((line = err.readLine()) != null) sb.append(line).append("\n");
            p.waitFor();
            String out = sb.toString().trim();
            return out.length() > 0 ? out : "OK (exit=" + p.exitValue() + ")";
        } catch (Exception e) {
            return "Error: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
}
