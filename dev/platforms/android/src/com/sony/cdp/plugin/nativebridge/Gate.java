package com.sony.cdp.plugin.nativebridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaPreferences;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;


/**
 * @class Gate
 * @brief NativeBridge と通信するベースクラス
 *         com.sony.cdp.plugin.nativebridge クライアントは本クラスから Gate クラスを派生する
 */
public class Gate {
    private static final String TAG = "[com.sony.cdp.plugin.nativebridge][Native][Gate] ";

    /**
     * @class Context
     * @brief Native Bridge Context 情報を格納
     */
    protected final class Context {
        public final CordovaInterface   cordova;
        public final CordovaWebView     webView;
        public final CordovaPreferences preferences;
        public final CallbackContext    callbackContext;
        public final String             className;
        public final String             methodName;
        public final String             objectId;
        public final String             taskId;
        public final boolean           compatible;
        public final String             threadId = Thread.currentThread().getName();
        public        boolean           needSendResult = true;
        Context(CordovaPlugin plugin, CordovaPreferences preferences, CallbackContext ctx, JSONObject execInfo) throws JSONException {
            this.cordova            = plugin.cordova;
            this.webView            = plugin.webView;
            this.preferences        = preferences;
            this.callbackContext    = ctx;
            JSONObject feature = execInfo.getJSONObject("feature");
            this.className  = feature.getJSONObject("android").getString("packageInfo");
            this.methodName = execInfo.isNull("method") ? null : execInfo.getString("method");
            this.objectId   = execInfo.getString("objectId");
            this.taskId     = execInfo.isNull("taskId") ? null : execInfo.getString("taskId");
            this.compatible = execInfo.getBoolean("compatible");
        }
    }

    private Context mCurrentContext = null;

    ///////////////////////////////////////////////////////////////////////
    // public methods

    /**
     * Cordova 互換ハンドラ (JSONArray 版)
     * NativeBridge からコールされる
     * compatible オプションが有効な場合、このメソッドがコールされる
     * クライアントは本メソッドをオーバーライド可能
     *
     * @param action  [in] アクション名.
     * @param args    [in] exec() 引数.
     * @param context [in] Gate.Context を格納. CallbackContext へは context.callbackContextでアクセス可
     * @return  action の成否 true:成功 / false: 失敗
     */
    public boolean execute(String action, JSONArray args, Context context) throws JSONException {
        return execute(action, new CordovaArgs(args), context);
    }

    /**
     * Cordova 互換ハンドラ (CordovaArgs 版)
     * NativeBridge からコールされる
     * compatible オプションが有効な場合、このメソッドがコールされる
     * クライアントは本メソッドをオーバーライド可能
     *
     * @param action  [in] アクション名.
     * @param args    [in] exec() 引数.
     * @param context [in] Gate.Context を格納. CallbackContext へは context.callbackContextでアクセス可
     * @return  action の成否 true:成功 / false: 失敗
     */
    public boolean execute(String action, CordovaArgs args, Context context) throws JSONException {
        Log.w(TAG, "execute() method should be override from sub class.");
        return false;
    }

    /**
     * メソッド呼び出し
     * NativeBridge からコールされる
     *
     * @param mehtodName    [in] 呼び出し対象のメソッド名
     * @param args          [in] exec() の引数リスト
     * @param context        [in] Callback Context
     * @return ハンドリング時に true を返却
     */
    public boolean invoke(String methodName, JSONArray args, Context context) {
        synchronized (this) {
            try {
                Class<?> cls = this.getClass();
                int length = args.length();
                Class<?>[] argTypes = new Class[length];
                Object[] argValues = new Object[length];
                for (int i = 0; i < length; i++) {
                    Object arg = args.get(i);
                    argTypes[i] = normalizeType(arg.getClass());
                    argValues[i] = arg;
                }
                Method method = cls.getMethod(methodName, argTypes);

                mCurrentContext = context;
                method.invoke(this, argValues);
                if (mCurrentContext.needSendResult) {
                    MessageUtils.sendSuccessResult(context.callbackContext, context.taskId);
                }
                return true;

            } catch (JSONException e) {
                Log.e(TAG, "Invalid JSON object", e);
            } catch (NoSuchMethodException e) {
                Log.d(TAG, "method not found", e);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "Illegal Access", e);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid Arg", e);
            } catch (InvocationTargetException e) {
                Log.e(TAG, "Invocation Target Exception", e);
            } finally {
                mCurrentContext = null;
            }
        }
        return false;
    }

    /**
     * cancel 呼び出し
     * NativeBridge からコールされる。
     * クライアントは本メソッドをオーバーライド可能
     *
     * @param context [in] The execute context. (NativeBridge extended argument)
     */
    public void cancel(Context context) {
        return;
    }

    ///////////////////////////////////////////////////////////////////////
    // public static methods

    /**
     * Native Bridge が使用する Gate.Context の生成
     * NativeBridge からコールされる
     *
     * @param plugin          [in] cordova plugin
     * @param preferences     [in] cordova preferences
     * @param callbackContext [in] callback context
     * @param execInfo        [in] JavaSript 情報
     * @throws JSONException
     */
    public static Context newContext(CordovaPlugin plugin, CordovaPreferences preferences, CallbackContext callbackContext, JSONObject execInfo) throws JSONException {
        return new Gate().new Context(plugin, preferences, callbackContext, execInfo);
    }

    ///////////////////////////////////////////////////////////////////////
    // protected methods

    /**
     * Context の取得
     * getContext() のヘルパー関数。 既定で autoSendResult を false に設定する。
     *
     * @return Context オブジェクト
     */
    protected Context getContext() {
        return getContext(false);
    }

    /**
     * Context の取得
     * method 呼び出されたスレッドからのみ Context 取得が可能
     * compatible オプションを伴って呼ばれた場合は無効になる。
     *
     * @param  autoSendResult [in] Framework 内で暗黙的に sendResult() する場合には true を指定
     * @return Context オブジェクト
     */
    protected Context getContext(boolean autoSendResult) {
        synchronized (this) {
            if (null != mCurrentContext && Thread.currentThread().getName().equals(mCurrentContext.threadId)) {
                mCurrentContext.needSendResult = autoSendResult;
                return mCurrentContext;
            } else {
                Log.e(TAG, "Calling getContext() is permitted only from method entry thread.");
                return null;
            }
        }
    }

    /**
     * 結果を JavaScript へ返却
     * 関数の return ステートメント同等のセマンティックスを持つ
     * method 呼び出されたスレッドからのみコール可能
     * keepCallback は false が指定される。
     *
     * @param param [in] Native から JavaScript へ返す値を指定
     */
    protected void returnParames(Object param) {
        if (null != mCurrentContext && Thread.currentThread().getName().equals(mCurrentContext.threadId)) {
            mCurrentContext.needSendResult = false;
            MessageUtils.sendSuccessResult(mCurrentContext.callbackContext, MessageUtils.makeMessage(mCurrentContext.taskId, param));
        } else {
            Log.e(TAG, "Calling returnMessage() is permitted only from method entry thread.");
        }
    }

    /**
     * 値を JavaScript へ通知
     * sendPluginResult() のヘルパー関数。 既定で keepCallback を有効にする。
     *
     * @param context [in] context オブジェクトを指定
     * @param params  [in] パラメータを可変引数で指定
     */
    protected void notifyParams(Context context, Object... params) {
        notifyParams(true, context, params);
    }

    /**
     * 値を JavaScript へ通知
     * sendPluginResult() のヘルパー関数
     *
     * @param keepCallback [in] keepCallback 値
     * @param context      [in] context オブジェクトを指定
     * @param params       [in] パラメータを可変引数で指定
     */
    protected void notifyParams(boolean keepCallback, Context context, Object... params) {
        if (null == context || null == context.callbackContext) {
            Log.e(TAG, "Invalid context object.");
            return;
        }
        int resultCode = keepCallback ? MessageUtils.SUCCESS_PROGRESS : MessageUtils.SUCCESS_OK;
        PluginResult result = new PluginResult(PluginResult.Status.OK, MessageUtils.makeMessage(resultCode, null, context.taskId, params));
        result.setKeepCallback(keepCallback);
        context.callbackContext.sendPluginResult(result);
    }

    /**
     * 値を JavaScript へ通知
     * ワーカースレッドから使用可能
     * keepCallback は false が指定される
     *
     * @param context [in] context オブジェクトを指定
     * @param params  [in] パラメータを可変引数で指定
     */
    protected void resolveParams(Context context, Object... params) {
        if (null == context || null == context.callbackContext) {
            Log.e(TAG, "Invalid context object.");
            return;
        }
        MessageUtils.sendSuccessResult(context.callbackContext, MessageUtils.makeMessage(context.taskId, params));
    }

    /**
     * 値を JavaScript へエラーを通知
     * ヘルパー関数
     * keepCallback は false が指定される
     *
     * @param context [in] context オブジェクトを指定
     * @param params  [in] パラメータを可変引数で指定
     */
    protected void rejectParams(Context context, Object... params) {
        rejectParams(MessageUtils.ERROR_FAIL, null, context, params);
    }

    /**
     * 値を JavaScript へエラーを通知
     * ワーカースレッドから使用可能
     * keepCallback は false が指定される
     *
     * @param code    [in] エラーコード
     * @param message [in] エラーメッセージ
     * @param context [in] context オブジェクトを指定
     * @param params  [in] パラメータを可変引数で指定
     */
    protected void rejectParams(int code, String message, Context context, Object... params) {
        if (null == context || null == context.callbackContext) {
            Log.e(TAG, "Invalid context object.");
            return;
        }
        MessageUtils.sendErrorResult(context.callbackContext, MessageUtils.makeMessage(code, message, context.taskId, params));
    }

    ///////////////////////////////////////////////////////////////////////
    // private methods

    /**
     * 型の正規化
     * オブジェクトをプリミティブに変換する
     * 数値型は、すべて double にする (JavaScript との対象性より)
     *
     * @param src [in] 型情報
     * @return 正規化された型情報
     */
    private Class<?> normalizeType(Class<?> src) {
        String type = src.getName();
        if (    type.equals("java.lang.Byte")
            ||  type.equals("java.lang.Short")
            ||  type.equals("java.lang.Integer")
            ||  type.equals("java.lang.Long")
            ||  type.equals("java.lang.Float")
            ||  type.equals("java.lang.Double")
        ) {
            return double.class;
        } else if (type.equals("java.lang.Boolean")) {
            return boolean.class;
        } else {
            return src;
        }
    }

}
