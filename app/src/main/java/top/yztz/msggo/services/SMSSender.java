/*
 * Copyright (C) 2026 yztz
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */


package top.yztz.msggo.services;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import java.util.ArrayList;
import java.util.List;

public class SMSSender {
    public static final String SENT_SMS_ACTION = "top.yztz.msggo.action.SENT_SMS_ACTION";

    public static SubscriptionInfo getSubBySlotId(Context context, int slot) {
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        @SuppressLint("MissingPermission") List<SubscriptionInfo> subscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();
        if (subscriptionInfoList == null) return null;
        
        for (SubscriptionInfo sub : subscriptionInfoList) {
            if (sub.getSimSlotIndex() == slot) return sub;
        }
        return null;
    }

    public static SubscriptionInfo getSubBySubscriptionId(Context context, int subId) {
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        @SuppressLint("MissingPermission") List<SubscriptionInfo> subscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();
        if (subscriptionInfoList == null) return null;

        for (SubscriptionInfo sub : subscriptionInfoList) {
            if (sub.getSubscriptionId() == subId) return sub;
        }
        return null;
    }

    public static int getDefaultSubID() {
        return SubscriptionManager.getDefaultSmsSubscriptionId();
    }

    public static List<SubscriptionInfo> getSubs(Context context) {
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        @SuppressLint("MissingPermission") List<SubscriptionInfo> subscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();
        return subscriptionInfoList != null ? subscriptionInfoList : new ArrayList<>();
    }

    /**
     * 发送短信
     *
     * @param context     上下文
     * @param content     发送内容
     * @param phoneNumber 电话号码
     * @param subId       SIM 卡订阅 ID
     * @param code        独一无二的请求码（用以广播接收）
     * @param statusToken 当前发送会话的状态校验令牌
     * @return 是否已成功提交给系统短信服务
     */
    public static boolean sendMessage(Context context, String content, String phoneNumber,
                                      int subId, int code, String statusToken) {
        try {
            final SmsManager manager;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                manager = context.getSystemService(SmsManager.class).createForSubscriptionId(subId);
            } else {
                manager = SmsManager.getSmsManagerForSubscriptionId(subId);
            }

            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

            ArrayList<String> msgs = manager.divideMessage(content);
            int messageCount = msgs.size();

            if (messageCount > 1) {
                ArrayList<PendingIntent> sentIntents = new ArrayList<>();
                for (int i = 0; i < messageCount; i++) {
                    Intent sentIntent = createSentIntent(context, code, phoneNumber, i,
                            messageCount, statusToken);
                    sentIntents.add(PendingIntent.getBroadcast(context,
                            code * 1000 + i, sentIntent, flags));
                }
                manager.sendMultipartTextMessage(phoneNumber, null, msgs, sentIntents, null);
            } else {
                Intent sentIntent = createSentIntent(context, code, phoneNumber, 0, 1,
                        statusToken);
                PendingIntent sentPI = PendingIntent.getBroadcast(context, code, sentIntent, flags);
                manager.sendTextMessage(phoneNumber, null, content, sentPI, null);
            }
            return true;
        } catch (Exception e) {
            android.util.Log.e("SMSSender", "Failed to send SMS", e);
            return false;
        }
    }

    private static Intent createSentIntent(Context context, int code, String phoneNumber,
                                           int part, int totalParts, String statusToken) {
        Intent intent = new Intent(SENT_SMS_ACTION);
        // Keep the PendingIntent callback inside this app even though the runtime receiver must
        // be exported to receive results from the privileged telephony process.
        intent.setPackage(context.getPackageName());
        intent.putExtra("code", code);
        intent.putExtra("phone", phoneNumber);
        intent.putExtra("part", part);
        intent.putExtra("totalParts", totalParts);
        intent.putExtra("statusToken", statusToken);
        return intent;
    }
}
