package com.orangelabs.rcs.ri.extension;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.gsma.services.rcs.extension.InstantMultimediaMessageIntent;
import com.orangelabs.rcs.ri.R;
import com.orangelabs.rcs.ri.utils.LogUtils;
import com.orangelabs.rcs.ri.utils.Utils;

/**
 * Messaging session invitation receiver
 *
 * @author jmauffret
 */
public class InstantMessageReceiver extends BroadcastReceiver {
    private static final String LOGTAG = LogUtils.getTag(InstantMessageReceiver.class
            .getSimpleName());

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!InstantMultimediaMessageIntent.ACTION_NEW_INSTANT_MESSAGE.equals(action)) {
            if (LogUtils.isActive) {
                Log.d(LOGTAG, "Unknown action=".concat(action));
            }
            return;
        }

        /* Display instant message content */
        String content = new String(intent.getByteArrayExtra(InstantMultimediaMessageIntent.EXTRA_CONTENT));
        Utils.displayLongToast(context, context.getString(R.string.label_recv_instant_messsage) + "\n" + content);
    }
}