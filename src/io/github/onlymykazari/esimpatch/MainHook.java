package io.github.onlymykazari.esimpatch;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * OnePlus 13T (converted to OxygenOS 13s) eSIM entry patch.
 *
 * Two hooks are installed:
 *
 * 1. com.android.phone
 *    The "SIM & eSIM settings" entry launches EsimSettingsActivity, which on
 *    this ROM is a dead page (internal eUICC absent). Redirect it straight to
 *    the native Google LPA manager (MANAGE_EMBEDDED_SUBSCRIPTIONS ->
 *    CurrentProfileListActivity).
 *
 * 2. com.google.android.euicc
 *    The Google LPA "Add SIM" entry (ProfileListFragment.mButtonAddProfile)
 *    hits the framework EID limit (0x20009) on this device, so redirect it to
 *    EasyEUICC's DownloadWizardActivity instead.
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "EsimPatch";

    private static final String PKG_PHONE = "com.android.phone";
    private static final String PKG_GOOGLE_LPA = "com.google.android.euicc";

    private static final String CLS_ESIM_SETTINGS =
            "com.android.phone.esim.settings.EsimSettingsActivity";
    private static final String CLS_PROFILE_FRAG =
            "com.android.euicc.ui.settings.ProfileListFragment";

    private static final String ACTION_MANAGE =
            "android.telephony.euicc.action.MANAGE_EMBEDDED_SUBSCRIPTIONS";

    private static final String EASY_PKG = "im.angry.easyeuicc.ce";
    private static final String EASY_DOWNLOAD_ACTIVITY =
            "im.angry.openeuicc.ui.wizard.DownloadWizardActivity";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (PKG_PHONE.equals(lpparam.packageName)) {
            hookPhone(lpparam);
        } else if (PKG_GOOGLE_LPA.equals(lpparam.packageName)) {
            hookGoogleLpa(lpparam);
        }
    }

    private void hookPhone(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(CLS_ESIM_SETTINGS, lpparam.classLoader,
                    "onCreate", Bundle.class, new RedirectToLpaHook());
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook phone failed: " + t);
        }
    }

    private void hookGoogleLpa(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(CLS_PROFILE_FRAG, lpparam.classLoader,
                    "onPreferenceClick", "androidx.preference.Preference",
                    new AddSimHook());
            XposedBridge.log(TAG + ": hooked Google LPA Add SIM");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook Google LPA failed: " + t);
        }
    }

    /** Redirect the SIM&eSIM entry to the native Google LPA manager. */
    private static class RedirectToLpaHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            try {
                Activity activity = (Activity) param.thisObject;
                Intent intent = new Intent(ACTION_MANAGE);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
                activity.finish();
                XposedBridge.log(TAG + ": redirected SIM&eSIM entry to native LPA");
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": redirect failed: " + t);
            }
        }
    }

    /** Redirect "Add SIM" to EasyEUICC's download wizard. */
    private static class AddSimHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                Object pref = param.args[0];
                if (pref == null) {
                    return;
                }
                Object addBtn = XposedHelpers.getObjectField(
                        param.thisObject, "mButtonAddProfile");
                if (addBtn == null || pref != addBtn) {
                    return;
                }
                Context ctx = (Context) XposedHelpers.callMethod(
                        param.thisObject, "getContext");
                if (ctx == null) {
                    XposedBridge.log(TAG + ": context null, skip");
                    return;
                }
                Intent intent = new Intent();
                intent.setComponent(
                        new ComponentName(EASY_PKG, EASY_DOWNLOAD_ACTIVITY));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
                param.setResult(true);
                XposedBridge.log(TAG + ": Add SIM -> EasyEUICC DownloadWizard");
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": add sim redirect failed: " + t);
            }
        }
    }
}
