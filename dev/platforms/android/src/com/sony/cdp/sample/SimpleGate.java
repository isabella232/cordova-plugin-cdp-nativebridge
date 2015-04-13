package com.sony.cdp.sample;

import java.util.HashSet;
import java.util.Set;

import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.sony.cdp.plugin.nativebridge.Gate;
import com.sony.cdp.plugin.nativebridge.MessageUtils;


/**
 * @class SimpleGate
 * @brief サンプル Gate クラス
 */
public class SimpleGate extends Gate {
    private static final String TAG = "[com.sony.cdp.sample][Native][SimpleGate] ";

    private Set<String> mCancelableTask = new HashSet<String>();

    ///////////////////////////////////////////////////////////////////////
    // public mehtods

    /**
	 * サンプルメソッド
	 * JavaScript レイヤで指定したメソッドと引数を受けることができる
	 * 数値は double 固定
	 *
	 * 値を戻すには
	 *  - returnParams()
	 * を使用する。
	 *
	 * @throws JSONException
	 */
	public void coolMethod(double arg1, boolean arg2, String arg3, JSONObject arg4) throws JSONException {
	    String msg = "arg1: " + String.valueOf((int)arg1) + ", arg2: " + String.valueOf(arg2) + ", arg3: " + arg3;
	    msg += (", 日本語でOK: " + String.valueOf(arg4.getBoolean("ok")));
	    returnParames(msg);
	}

    /**
     * サンプルメソッド
     * void 版
     *
     */
    public void voidMethod() {
        Log.d(TAG, "void voidMethod(void), called.");
    }

    /**
     * サンプルメソッド (スレッドを扱う例)
     * 引数に "final" を指定しても、リフレクションコール可能
     * getContext() より、cordova plugin が扱う変数にアクセスが可能
     *
     * スレッド内では
     *  - notifyParams()
     *  - resolveParams()
     *  - rejectParams()
     * がそれぞれ使用可能
     *
     * @throws JSONException
     */
    public void threadMethod(final double arg1, final boolean arg2, final String arg3, final JSONObject arg4) throws JSONException {
        final Context context = getContext();

        context.cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                String errorMsg;
                try {
                    notifyParams(context, (int)arg1, arg2);
                    notifyParams(context, arg3, arg4);
                    String msg = "arg1: " + String.valueOf((int)arg1) + ", arg2: " + String.valueOf(arg2) + ", arg3: " + arg3;
                    msg += (", 日本語でOK: " + String.valueOf(arg4.getBoolean("ok")));
                    resolveParams(context, msg);
                } catch (JSONException e) {
                    errorMsg = "Invalid JSON object";
                    Log.e(TAG, errorMsg, e);
                    rejectParams(MessageUtils.ERROR_FAIL, errorMsg, context);
                }
            }
        });
    }

    /**
     * ワーカースレッドとキャンセルの例
     * cancel() がコールされるまで、100 [msec] ごとに進捗を通知するサンプル
     *
     * @throws JSONException
     */
    public void progressMethod() throws JSONException {
        final Context context = getContext();

        synchronized (this) {
            mCancelableTask.add(context.taskId);
        }

        context.cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                String errorMsg;
                int progress = 0;
                try {
                    while (true) {
                        if (isCanceled(context.taskId)) {
                            rejectParams(MessageUtils.ERROR_CANCEL, TAG + "progressMethod() canceled.", context);
                            break;
                        }
                        notifyParams(context, progress);
                        progress++;
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    errorMsg = "InterruptedException occur.";
                    Log.e(TAG, errorMsg, e);
                    rejectParams(MessageUtils.ERROR_FAIL, errorMsg, context);
                }
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////
    // Override: NativeBridge

    /**
     * Cordova 互換ハンドラ
     * NativeBridge からコールされる
     * compatible オプションが有効な場合、このメソッドがコールされる
     * 拡張情報は context に格納される。
     * クライアントは本メソッドをオーバーライド可能
     *
     * @param action          The action to execute.
     * @param args            The exec() arguments.
     * @param callbackContext The callback context used when calling back into JavaScript.
     * @param context         The execute context. (NativeBridge extended argument)
     * @return                Whether the action was valid.
     */
	@Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext, Context context) throws JSONException {
	    if (action.equals("compatibleCheck")) {
	        JSONArray message = new JSONArray();
	        message.put(context.taskId);
	        JSONObject argsInfo = new JSONObject();
	        argsInfo.put("taskId", context.taskId);
	        argsInfo.put("arg1", args.getInt(0));
	        argsInfo.put("arg2", args.getBoolean(1));
	        argsInfo.put("arg3", args.getString(2));
	        argsInfo.put("arg4", args.getJSONObject(3));
            message.put(argsInfo);
	        callbackContext.success(message);
	        return true;
	    }
        return false;
    }

    /**
     * cancel 呼び出し
     * NativeBridge からコールされる
     * クライアントは本メソッドをオーバーライドして、taskId を特定し処理を実装する
     * 全キャンセル時は taskId に null が格納されている
     *
     * @param context [in] The execute context. (NativeBridge extended argument)
     */
	@Override
    public void cancel(Context context) {
	    synchronized (this) {
	        if (null != context.taskId) {
	            mCancelableTask.remove(context.taskId);
	        } else {
	            mCancelableTask.clear();
	        }
	    }
    }

    ///////////////////////////////////////////////////////////////////////
    // private methods

    //! キャンセル確認
    private boolean isCanceled(String taskId) {
        synchronized (this) {
            return !mCancelableTask.contains(taskId);
        }
    }
}

